var RedactAnalisis = (function () {
  var opcionesBase = { ecmaVersion: 'latest', locations: true, allowHashBang: true };

  function copiarOpciones(tipoFuente) {
    return { ecmaVersion: opcionesBase.ecmaVersion, locations: true, allowHashBang: true, sourceType: tipoFuente };
  }

  function analizar(codigo) {
    try {
      return acorn.parse(codigo, copiarOpciones('script'));
    } catch (errorScript) {
      if (String(errorScript.message).indexOf("'import' and 'export'") >= 0) {
        return acorn.parse(codigo, copiarOpciones('module'));
      }
      throw errorScript;
    }
  }

  function validar(codigo) {
    try {
      analizar(codigo);
      return JSON.stringify([]);
    } catch (e) {
      var posicion = typeof e.pos === 'number' ? e.pos : 0;
      var mensaje = String(e.message).replace(/\s*\(\d+:\d+\)$/, '');
      return JSON.stringify([{
        linea: e.loc ? e.loc.line : 1,
        columna: e.loc ? e.loc.column + 1 : 1,
        mensaje: mensaje,
        alFinal: posicion >= codigo.replace(/\s+$/, '').length
      }]);
    }
  }

  function esNodo(valor) {
    return valor && typeof valor === 'object' && typeof valor.type === 'string';
  }

  function recorrer(nodo, visitar, padre) {
    if (!esNodo(nodo)) return;
    if (visitar(nodo, padre) === false) return;
    for (var clave in nodo) {
      if (clave === 'loc' || clave === 'start' || clave === 'end') continue;
      var hijo = nodo[clave];
      if (Array.isArray(hijo)) {
        for (var i = 0; i < hijo.length; i++) recorrer(hijo[i], visitar, nodo);
      } else if (esNodo(hijo)) {
        recorrer(hijo, visitar, nodo);
      }
    }
  }

  function esFuncion(nodo) {
    return nodo.type === 'FunctionDeclaration' || nodo.type === 'FunctionExpression' || nodo.type === 'ArrowFunctionExpression';
  }

  function nombreFuncion(nodo, padre) {
    if (nodo.id && nodo.id.name) return nodo.id.name;
    if (padre && padre.type === 'VariableDeclarator' && padre.id && padre.id.name) return padre.id.name;
    if (padre && padre.type === 'MethodDefinition' && padre.key) return padre.key.name || 'método';
    if (padre && padre.type === 'Property' && padre.key) return padre.key.name || 'función';
    return 'función anónima';
  }

  function complejidad(funcion) {
    var total = 1;
    recorrer(funcion.body, function (n) {
      if (n !== funcion.body && esFuncion(n)) return false;
      switch (n.type) {
        case 'IfStatement': case 'ConditionalExpression': case 'ForStatement': case 'ForInStatement':
        case 'ForOfStatement': case 'WhileStatement': case 'DoWhileStatement': case 'CatchClause':
          total++; break;
        case 'SwitchCase': if (n.test) total++; break;
        case 'LogicalExpression': total++; break;
      }
      return true;
    });
    return total;
  }

  function profundidadMaxima(nodo) {
    var maxima = 0;
    function bajar(n, nivel) {
      if (!esNodo(n)) return;
      var esBloqueControl = n.type === 'IfStatement' || n.type === 'ForStatement' || n.type === 'ForOfStatement' ||
        n.type === 'ForInStatement' || n.type === 'WhileStatement' || n.type === 'DoWhileStatement' || n.type === 'SwitchStatement' || n.type === 'TryStatement';
      var siguiente = esBloqueControl ? nivel + 1 : nivel;
      if (siguiente > maxima) maxima = siguiente;
      for (var clave in n) {
        if (clave === 'loc') continue;
        var hijo = n[clave];
        if (Array.isArray(hijo)) { for (var i = 0; i < hijo.length; i++) bajar(hijo[i], siguiente); }
        else if (esNodo(hijo) && !esFuncion(hijo)) bajar(hijo, siguiente);
      }
    }
    bajar(nodo.body, 0);
    return maxima;
  }

  function hallazgo(regla, nodo, extra) {
    return { regla: regla, linea: nodo.loc ? nodo.loc.start.line : 1, columna: nodo.loc ? nodo.loc.start.column + 1 : 1, extra: extra || '' };
  }

  function revisar(codigo) {
    var arbol;
    try { arbol = analizar(codigo); } catch (e) { return JSON.stringify([]); }
    var hallazgos = [];
    var declaradas = [];
    var usos = {};
    var reasignadas = {};

    recorrer(arbol, function (n, padre) {
      if (n.type === 'Identifier') {
        var esDeclaracion = padre && padre.type === 'VariableDeclarator' && padre.id === n;
        var esClave = padre && (padre.type === 'Property' && padre.key === n && !padre.shorthand && !padre.computed ||
          padre.type === 'MemberExpression' && padre.property === n && !padre.computed ||
          padre.type === 'MethodDefinition' && padre.key === n);
        if (!esDeclaracion && !esClave) usos[n.name] = (usos[n.name] || 0) + 1;
      }
      if (n.type === 'VariableDeclaration') {
        if (n.kind === 'var') hallazgos.push(hallazgo('JS_VAR', n));
        for (var i = 0; i < n.declarations.length; i++) {
          var d = n.declarations[i];
          if (d.id && d.id.type === 'Identifier') declaradas.push({ nombre: d.id.name, nodo: d, tipo: n.kind });
        }
      }
      if (n.type === 'AssignmentExpression' && n.left.type === 'Identifier') reasignadas[n.left.name] = true;
      if (n.type === 'UpdateExpression' && n.argument.type === 'Identifier') reasignadas[n.argument.name] = true;
      if (n.type === 'BinaryExpression' && (n.operator === '==' || n.operator === '!=')) hallazgos.push(hallazgo('JS_IGUALDAD_DEBIL', n, n.operator));
      if (n.type === 'CallExpression' && n.callee.type === 'Identifier' && n.callee.name === 'eval') hallazgos.push(hallazgo('SEG_EVAL', n));
      if (n.type === 'NewExpression' && n.callee.type === 'Identifier' && n.callee.name === 'Function') hallazgos.push(hallazgo('SEG_EVAL', n));
      if (n.type === 'AssignmentExpression' && n.left.type === 'MemberExpression' && n.left.property &&
        (n.left.property.name === 'innerHTML' || n.left.property.name === 'outerHTML')) hallazgos.push(hallazgo('SEG_INNERHTML', n));
      if (n.type === 'CallExpression' && n.callee.type === 'MemberExpression' && n.callee.object.name === 'document' &&
        n.callee.property && n.callee.property.name === 'write') hallazgos.push(hallazgo('SEG_DOCUMENT_WRITE', n));
      if (n.type === 'CatchClause' && n.body.body.length === 0) hallazgos.push(hallazgo('CATCH_VACIO', n));
      if (esFuncion(n)) {
        var nombre = nombreFuncion(n, padre);
        var c = complejidad(n);
        if (c > 10) hallazgos.push(hallazgo('FUNCION_COMPLEJA', n, nombre + '|' + c));
        var lineas = n.loc.end.line - n.loc.start.line + 1;
        if (lineas > 50) hallazgos.push(hallazgo('FUNCION_LARGA', n, nombre + '|' + lineas));
        if (n.params && n.params.length > 5) hallazgos.push(hallazgo('MUCHOS_PARAMETROS', n, nombre + '|' + n.params.length));
        if (n.body && n.body.type === 'BlockStatement' && profundidadMaxima(n) > 4) hallazgos.push(hallazgo('ANIDAMIENTO_PROFUNDO', n, nombre));
      }
      return true;
    }, null);

    for (var k = 0; k < declaradas.length; k++) {
      var v = declaradas[k];
      if (!usos[v.nombre]) hallazgos.push(hallazgo('VARIABLE_NO_USADA', v.nodo, v.nombre));
      else if (v.tipo === 'let' && !reasignadas[v.nombre] && v.nodo.init) hallazgos.push(hallazgo('PREFERIR_CONST', v.nodo, v.nombre));
    }
    return JSON.stringify(hallazgos);
  }

  function describirExpresion(e) {
    if (!e) return {};
    if (e.type === 'CallExpression') {
      var callee = e.callee;
      var nombre = callee.type === 'Identifier' ? callee.name :
        (callee.type === 'MemberExpression' ? textoMiembro(callee) : 'función');
      return { tipo: 'llamada', nombre: nombre, cantidad: e.arguments.length };
    }
    if (e.type === 'AssignmentExpression') {
      return { tipo: 'asignacion', nombre: e.left.type === 'Identifier' ? e.left.name : textoMiembro(e.left), operador: e.operator };
    }
    if (e.type === 'UpdateExpression') {
      return { tipo: 'incremento', nombre: e.argument.name || 'valor', operador: e.operator };
    }
    if (e.type === 'AwaitExpression') return { tipo: 'espera' };
    return { tipo: 'expresion' };
  }

  function textoMiembro(m) {
    if (!m || m.type !== 'MemberExpression') return m && m.name ? m.name : 'valor';
    var objeto = m.object.type === 'Identifier' ? m.object.name : (m.object.type === 'MemberExpression' ? textoMiembro(m.object) : (m.object.type === 'ThisExpression' ? 'this' : 'objeto'));
    var propiedad = m.computed ? '[...]' : (m.property.name || 'propiedad');
    return objeto + '.' + propiedad;
  }

  function tipoValor(init) {
    if (!init) return 'sin_valor';
    switch (init.type) {
      case 'Literal':
        if (typeof init.value === 'number') return 'numero';
        if (typeof init.value === 'string') return 'cadena';
        if (typeof init.value === 'boolean') return 'booleano';
        if (init.value === null) return 'nulo';
        return 'literal';
      case 'TemplateLiteral': return 'cadena';
      case 'ArrayExpression': return 'lista';
      case 'ObjectExpression': return 'objeto';
      case 'ArrowFunctionExpression': case 'FunctionExpression': return 'funcion';
      case 'CallExpression': return 'llamada';
      case 'NewExpression': return 'instancia';
      case 'BinaryExpression': return 'operacion';
      default: return 'expresion';
    }
  }

  function explicarLineas(codigo) {
    var arbol;
    try { arbol = analizar(codigo); } catch (e) { return JSON.stringify({ error: String(e.message), lineas: [] }); }
    var lineas = [];
    function agregar(n, nivel, datos) {
      datos.linea = n.loc.start.line;
      datos.nivel = nivel;
      lineas.push(datos);
    }
    function cuerpo(b) {
      if (!b) return [];
      if (b.type === 'BlockStatement') return b.body;
      return [b];
    }
    function visitar(lista, nivel) {
      for (var i = 0; i < lista.length; i++) {
        var n = lista[i];
        switch (n.type) {
          case 'VariableDeclaration':
            for (var j = 0; j < n.declarations.length; j++) {
              var d = n.declarations[j];
              var tv = tipoValor(d.init);
              if (tv === 'funcion') {
                agregar(d, nivel, { tipo: 'funcion', nombre: d.id.name || 'función', cantidad: d.init.params.length, extra: n.kind });
                visitar(cuerpo(d.init.body), nivel + 1);
              } else {
                agregar(d, nivel, { tipo: 'declaracion_variable', nombre: d.id.name || 'desestructuración', extra: n.kind, valor: tv });
              }
            }
            break;
          case 'FunctionDeclaration':
            agregar(n, nivel, { tipo: 'funcion', nombre: n.id ? n.id.name : 'función', cantidad: n.params.length, extra: n.async ? 'async' : '' });
            visitar(n.body.body, nivel + 1);
            break;
          case 'ClassDeclaration':
            agregar(n, nivel, { tipo: 'clase', nombre: n.id ? n.id.name : 'clase', extra: n.superClass && n.superClass.name ? n.superClass.name : '' });
            for (var m = 0; m < n.body.body.length; m++) {
              var metodo = n.body.body[m];
              if (metodo.type === 'MethodDefinition') {
                agregar(metodo, nivel + 1, { tipo: 'metodo', nombre: metodo.key.name || 'método', cantidad: metodo.value.params.length });
                visitar(metodo.value.body.body, nivel + 2);
              }
            }
            break;
          case 'ReturnStatement':
            agregar(n, nivel, { tipo: 'retorno', valor: tipoValor(n.argument) });
            break;
          case 'IfStatement':
            agregar(n, nivel, { tipo: 'condicional' });
            visitar(cuerpo(n.consequent), nivel + 1);
            if (n.alternate) {
              if (n.alternate.type === 'IfStatement') visitar([n.alternate], nivel);
              else { agregar(n.alternate, nivel, { tipo: 'alternativa' }); visitar(cuerpo(n.alternate), nivel + 1); }
            }
            break;
          case 'ForStatement':
            agregar(n, nivel, { tipo: 'bucle_for' });
            visitar(cuerpo(n.body), nivel + 1);
            break;
          case 'ForOfStatement': case 'ForInStatement':
            var variable = n.left.type === 'VariableDeclaration' ? (n.left.declarations[0].id.name || 'elemento') : (n.left.name || 'elemento');
            agregar(n, nivel, { tipo: n.type === 'ForOfStatement' ? 'bucle_por_cada' : 'bucle_por_clave', nombre: variable, extra: n.right.name || '' });
            visitar(cuerpo(n.body), nivel + 1);
            break;
          case 'WhileStatement': case 'DoWhileStatement':
            agregar(n, nivel, { tipo: 'bucle_while' });
            visitar(cuerpo(n.body), nivel + 1);
            break;
          case 'SwitchStatement':
            agregar(n, nivel, { tipo: 'seleccion', cantidad: n.cases.length });
            for (var s = 0; s < n.cases.length; s++) visitar(n.cases[s].consequent, nivel + 1);
            break;
          case 'TryStatement':
            agregar(n, nivel, { tipo: 'intento' });
            visitar(n.block.body, nivel + 1);
            if (n.handler) { agregar(n.handler, nivel, { tipo: 'captura' }); visitar(n.handler.body.body, nivel + 1); }
            break;
          case 'ThrowStatement': agregar(n, nivel, { tipo: 'lanzar' }); break;
          case 'BreakStatement': agregar(n, nivel, { tipo: 'romper' }); break;
          case 'ContinueStatement': agregar(n, nivel, { tipo: 'continuar' }); break;
          case 'ImportDeclaration': agregar(n, nivel, { tipo: 'importacion', nombre: String(n.source.value) }); break;
          case 'ExpressionStatement':
            var desc = describirExpresion(n.expression);
            agregar(n, nivel, desc);
            if (n.expression.type === 'CallExpression') {
              for (var a = 0; a < n.expression.arguments.length; a++) {
                var arg = n.expression.arguments[a];
                if (arg.type === 'ArrowFunctionExpression' || arg.type === 'FunctionExpression') visitar(cuerpo(arg.body), nivel + 1);
              }
            }
            break;
          case 'BlockStatement': visitar(n.body, nivel); break;
          default: agregar(n, nivel, { tipo: 'expresion' });
        }
      }
    }
    visitar(arbol.body, 0);
    return JSON.stringify({ error: null, lineas: lineas });
  }

  return { validar: validar, revisar: revisar, explicarLineas: explicarLineas };
})();
