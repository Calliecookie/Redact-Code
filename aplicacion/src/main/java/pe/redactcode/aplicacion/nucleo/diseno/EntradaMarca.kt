package pe.redactcode.aplicacion.nucleo.diseno

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object EntradaMarca {
    fun debeAnimarEntrada(primeraCreacion: Boolean, animacionesActivadas: Boolean, animadoresDelSistema: Boolean): Boolean =
        primeraCreacion && animacionesActivadas && animadoresDelSistema

    val Curva = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

@Composable
fun Modifier.entradaSuave(
    activa: Boolean,
    retardoMs: Int = 0,
    duracionMs: Int = 200,
    desplazamiento: Dp = 8.dp,
    escalaInicial: Float = 1f,
    clave: String = "entrada"
): Modifier {
    var hecha by rememberSaveable(clave) { mutableStateOf(!activa) }
    var lanzada by rememberSaveable("lanzada_$clave") { mutableStateOf(!activa) }
    LaunchedEffect(Unit) { if (!lanzada) { lanzada = true; hecha = true } }
    val progreso by animateFloatAsState(
        targetValue = if (hecha) 1f else 0f,
        animationSpec = tween(if (activa) duracionMs else 0, if (activa) retardoMs else 0, EntradaMarca.Curva),
        label = clave
    )
    val px = with(LocalDensity.current) { desplazamiento.toPx() }
    return this.alpha(progreso).graphicsLayer {
        translationY = (1f - progreso) * px
        val e = escalaInicial + (1f - escalaInicial) * progreso
        scaleX = e
        scaleY = e
    }
}
