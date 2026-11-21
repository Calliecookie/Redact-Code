import math
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

RAIZ = Path(__file__).resolve().parent.parent
ORIGINAL = Path(__file__).resolve().parent / "logo_redact_code_original.jpg"
RES = RAIZ / "aplicacion" / "src" / "main" / "res"
VISTAS = RAIZ / "build" / "derivados_logo"
UMBRAL = 18
AFILADO = ImageFilter.UnsharpMask(radius=1.0, percent=40, threshold=2)


def luminancia(imagen):
    return imagen.convert("L")


def detectar_baldosa(imagen):
    ancho, alto = imagen.size
    parche = [imagen.getpixel((x, y)) for x in range(0, 60, 4) for y in range(0, 60, 4)]
    fondo = tuple(sorted(c[i] for c in parche)[len(parche) // 2] for i in range(3))
    plano = Image.new("RGB", imagen.size, fondo)
    diferencia = luminancia(ImageChops.difference(imagen, plano))
    mascara = diferencia.point(lambda v: 255 if v > UMBRAL else 0)
    caja = mascara.getbbox()
    lado = caja[2] - caja[0]
    caja = (caja[0], caja[1], caja[2], caja[1] + lado)
    return fondo, mascara, caja


def radio_esquina(mascara, caja):
    x0, y0, x1, y1 = caja
    pasos = 0
    while pasos < 600 and mascara.getpixel((x0 + pasos, y0 + pasos)) == 0:
        pasos += 1
    distancia = pasos * math.sqrt(2)
    return distancia / (math.sqrt(2) - 1)


def mascara_redondeada(lado, radio, factor=4):
    grande = Image.new("L", (lado * factor, lado * factor), 0)
    ImageDraw.Draw(grande).rounded_rectangle([0, 0, lado * factor - 1, lado * factor - 1], radius=radio * factor, fill=255)
    return grande.resize((lado, lado), Image.LANCZOS)


def centro_de_la_r(baldosa):
    gris = baldosa.convert("RGB")
    ancho, alto = gris.size
    puntos = []
    paso = 2
    centro = ancho / 2
    limite = ancho * 0.36
    for y in range(0, alto, paso):
        for x in range(0, ancho, paso):
            if math.hypot(x - centro, y - centro) > limite:
                continue
            r, g, b = gris.getpixel((x, y))
            if max(r, g, b) > 170 and (g > 150 or b > 200):
                puntos.append((x, y))
    xs = [p[0] for p in puntos]
    ys = [p[1] for p in puntos]
    return (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, puntos


def mascaras_visibles(lado):
    visible = lado * 72 / 108
    desplazamiento = (lado - visible) / 2
    caja = [desplazamiento, desplazamiento, lado - desplazamiento - 1, lado - desplazamiento - 1]
    escala = 2
    resultado = {}
    for nombre, radio in (("circulo", visible / 2), ("rectangulo", visible * 0.25), ("squircle", visible * 0.40)):
        imagen = Image.new("L", (lado * escala, lado * escala), 0)
        ImageDraw.Draw(imagen).rounded_rectangle([c * escala for c in caja], radius=radio * escala, fill=255)
        resultado[nombre] = imagen.resize((lado, lado), Image.LANCZOS)
    return resultado


def elegir_lado(baldosa, mascara_baldosa, centro, puntos, radio_r):
    ancho = baldosa.size[0]
    borde = mascara_baldosa.filter(ImageFilter.MinFilter(9))
    borde = ImageChops.difference(mascara_baldosa, borde)
    pixeles_borde = [(x, y) for y in range(0, ancho, 3) for x in range(0, ancho, 3) if borde.getpixel((x, y)) > 128]
    for lado in range(2400, 900, -8):
        if radio_r > 0.333 * lado:
            continue
        pequena = 216
        factor = pequena / lado
        limpio = True
        for nombre, mascara in mascaras_visibles(pequena).items():
            for x, y in pixeles_borde:
                px = int(round((x - centro[0]) * factor + pequena / 2))
                py = int(round((y - centro[1]) * factor + pequena / 2))
                if 0 <= px < pequena and 0 <= py < pequena and mascara.getpixel((px, py)) > 8:
                    limpio = False
                    break
            if not limpio:
                break
        if limpio:
            return lado
    return None


def recortar_cuadrado(imagen, centro, lado, relleno):
    caja = (round(centro[0] - lado / 2), round(centro[1] - lado / 2), round(centro[0] + lado / 2), round(centro[1] + lado / 2))
    lienzo = Image.new("RGBA", (lado, lado), relleno + (255,))
    recorte = imagen.convert("RGBA").crop(caja)
    lienzo.paste(recorte, (0, 0), recorte)
    return lienzo


def muestrear_interior(baldosa):
    ancho = baldosa.size[0]
    arriba = baldosa.getpixel((ancho // 2, int(ancho * 0.12)))
    abajo = baldosa.getpixel((ancho // 2, int(ancho * 0.88)))
    return arriba[:3], abajo[:3]


def main():
    VISTAS.mkdir(parents=True, exist_ok=True)
    original = Image.open(ORIGINAL).convert("RGB")
    fondo, mascara, caja = detectar_baldosa(original)
    inset = 2
    caja = (caja[0] + inset, caja[1] + inset, caja[2] - inset, caja[3] - inset)
    lado = caja[2] - caja[0]
    radio = radio_esquina(mascara, (caja[0] - inset, caja[1] - inset, caja[2] + inset, caja[3] + inset))
    print("fondo", fondo, "caja", caja, "lado", lado, "radio", round(radio), "relacion", round(radio / lado, 3))
    recorte = original.crop(caja).convert("RGBA")
    forma = mascara_redondeada(lado, round(radio))
    recorte.putalpha(forma)

    baldosa = recorte.resize((1024, 1024), Image.LANCZOS).filter(AFILADO)
    alfa = mascara_redondeada(1024, round(radio * 1024 / lado))
    baldosa.putalpha(alfa)
    baldosa.save(RES / "drawable-nodpi" / "logo_redact_baldosa.png", optimize=True)

    inicio = Image.new("RGBA", (1152, 1152), (0, 0, 0, 0))
    pequena = recorte.resize((600, 600), Image.LANCZOS).filter(AFILADO)
    pequena.putalpha(mascara_redondeada(600, round(radio * 600 / lado)))
    inicio.paste(pequena, (276, 276), pequena)
    inicio.save(RES / "drawable-nodpi" / "logo_inicio.png", optimize=True)

    cx, cy, puntos = centro_de_la_r(recorte)
    centro_original = (cx + caja[0], cy + caja[1])
    radio_r = max(math.hypot(x - cx, y - cy) for x, y in puntos)
    print("centro de la R", (round(centro_original[0]), round(centro_original[1])), "radio R", round(radio_r))
    mascara_pura = Image.new("L", recorte.size, 0)
    mascara_pura.paste(forma, (0, 0))
    elegido = elegir_lado(recorte, mascara_pura, (cx, cy), puntos, radio_r)
    arriba, abajo = muestrear_interior(recorte)
    print("interior arriba", arriba, "abajo", abajo, "lado elegido", elegido)
    if elegido is None:
        print("opcion B requerida")
        sys.exit(2)
    base = recortar_cuadrado(recorte, (cx, cy), elegido, tuple(int((a + b) / 2) for a, b in zip(arriba, abajo)))
    tamanos = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    for densidad, px in tamanos.items():
        capa = base.resize((px, px), Image.LANCZOS).filter(AFILADO).convert("RGB")
        capa.save(RES / f"mipmap-{densidad}" / "ic_launcher_foreground.png", optimize=True)
    rgb = base.convert("RGB")
    lado_base = base.size[0]
    radio_util = 0.28 * lado_base
    alfa = Image.new("L", base.size, 0)
    datos_gris = rgb.load()
    datos_alfa = alfa.load()
    centro_base = lado_base / 2
    for y in range(lado_base):
        for x in range(lado_base):
            if math.hypot(x - centro_base, y - centro_base) > radio_util:
                continue
            valor = max(datos_gris[x, y])
            datos_alfa[x, y] = 0 if valor <= 120 else 255 if valor >= 190 else int((valor - 120) * 255 / 70)
    for densidad, px in tamanos.items():
        mono = Image.new("RGBA", (px, px), (255, 255, 255, 0))
        mono.putalpha(alfa.resize((px, px), Image.LANCZOS))
        mono.save(RES / f"mipmap-{densidad}" / "ic_launcher_monochrome.png", optimize=True)
    previa = Image.new("RGBA", (432, 432), (90, 90, 90, 255))
    previa.alpha_composite(Image.merge("RGBA", (Image.new("L", (432, 432), 255),) * 3 + (alfa.resize((432, 432), Image.LANCZOS),)))
    previa.save(VISTAS / "icono_monocromo.png")
    vista = base.resize((432, 432), Image.LANCZOS)
    for nombre, mascara_vista in mascaras_visibles(432).items():
        salida = Image.new("RGBA", (432, 432), (255, 255, 255, 255))
        salida.paste(vista, (0, 0), mascara_vista)
        salida.save(VISTAS / f"icono_{nombre}.png")
    print("fondo_icono sugerido", "#%02X%02X%02X" % tuple(int((a + b) / 2) for a, b in zip(arriba, abajo)))


main()
