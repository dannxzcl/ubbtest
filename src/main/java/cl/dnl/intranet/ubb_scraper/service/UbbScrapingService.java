package cl.dnl.intranet.ubb_scraper.service;

// --- AÑADE ESTOS IMPORTS ---
import cl.dnl.intranet.ubb_scraper.model.Usuario;
import cl.dnl.intranet.ubb_scraper.repository.UsuarioRepository;
// --- FIN DE IMPORTS NUEVOS ---

import cl.dnl.intranet.ubb_scraper.dto.AsignaturaDto;
import cl.dnl.intranet.ubb_scraper.dto.CarreraDto;
import cl.dnl.intranet.ubb_scraper.dto.DashboardDataDto;
import cl.dnl.intranet.ubb_scraper.dto.PonderacionDto;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

// Este código implementa la lógica de login automatizado a la intranet de la UBB, utilizando la
// biblioteca de Jsoup.
@Service
public class UbbScrapingService {

    // Creamos una clase interna para manejar la estructura compleja de las evaluaciones
    private static class Evaluacion {
        String nombre;
        int factorTotal;
        List<SubEvaluacion> subEvaluaciones = new ArrayList<>();
        double notaFinal = 0.0; // La nota final de esta evaluación (puede ser un promedio de sub-evaluaciones)

        Evaluacion(String nombre, int factorTotal) {
            this.nombre = nombre;
            this.factorTotal = factorTotal;
        }

        void calcularNotaFinal() {
            if (subEvaluaciones.isEmpty()) {
                // No hacer nada, la nota se asignará directamente
            } else {
                double sumaPonderada = 0;
                double sumaFactores = 0;
                for (SubEvaluacion sub : subEvaluaciones) {
                    if (sub.nota > 0) { // Solo considerar notas válidas
                        sumaPonderada += sub.nota * sub.factor;
                        sumaFactores += sub.factor;
                    }
                }
                if (sumaFactores > 0) {
                    this.notaFinal = sumaPonderada / sumaFactores;
                }
            }
        }
    }

    private static class SubEvaluacion {
        String nombre;
        int factor;
        double nota = -1.0; // -1 indica que no tiene nota aún

        SubEvaluacion(String nombre, int factor) {
            this.nombre = nombre;
            this.factor = factor;
        }
    }

    private static final String INTRANET_ROOT_URL = "https://intranet.ubiobio.cl/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";

    // --- INICIO DE LA MODIFICACIÓN ---
    private final UsuarioRepository usuarioRepository;
    private final EncryptionService encryptionService;

    // Modificamos el constructor para que Spring inyecte los nuevos componentes
    public UbbScrapingService(UsuarioRepository usuarioRepository, EncryptionService encryptionService) {
        this.usuarioRepository = usuarioRepository;
        this.encryptionService = encryptionService;
    }
    // --- FIN DE LA MODIFICACIÓN ---

    // Metodo principal que intenta iniciar sesión con un RUT y una contraseña, devolviendo un mapa con
    // el resultado del intento.
    public Map<String, Object> performLogin(String rutCompleto, String password) throws IOException {
        // 1. Autenticar contra la UBB (esta parte no cambia)
        Connection session = Jsoup.newSession().userAgent(USER_AGENT);
        session.url(INTRANET_ROOT_URL).method(Connection.Method.GET).execute();
        Connection.Response responseWithSessionId = session.url(INTRANET_ROOT_URL + "intranet/")
                .method(Connection.Method.GET)
                .followRedirects(false)
                .execute();

        if (responseWithSessionId.statusCode() != 302) {
            throw new IOException("No se recibió la segunda redirección esperada.");
        }
        String locationHeader = responseWithSessionId.header("Location");
        if (locationHeader == null || locationHeader.isEmpty()) {
            throw new IOException("La URL de redirección final está vacía.");
        }
        URL fullRedirectUrl = new URL(new URL(INTRANET_ROOT_URL), locationHeader);
        String path = fullRedirectUrl.getPath();
        String sessionId = path.substring(1).split("/")[0];
        String loginUrl = INTRANET_ROOT_URL + sessionId + "/intranet/inicio.php";
        String refererUrl = INTRANET_ROOT_URL + sessionId + "/intranet/";
        String[] rutParts = rutCompleto.replace(".", "").split("-");
        String rut = rutParts[0];
        String dv = rutParts[1];

        Connection.Response loginResponse = session.url(loginUrl)
                .method(Connection.Method.POST)
                .header("Origin", INTRANET_ROOT_URL.substring(0, INTRANET_ROOT_URL.length() - 1))
                .header("Referer", refererUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .data("rut", rut, "dv", dv, "rut_cambio", "", "rut_aux", rutCompleto, "clave", password)
                .followRedirects(true) // ¡Cambiamos a true para obtener la página final!
                .execute();

        // 2. Verificar si el login en la UBB fue exitoso
        String urlAfterLogin = loginResponse.url().toString();
        if (!urlAfterLogin.contains("inicio.php")) {
            // Si no llegamos a la página de inicio, el login falló.
            return Map.of("success", false);
        }

        // --- INICIO DE LA NUEVA LÓGICA DE BASE DE DATOS ---

        // 3. Si el login fue exitoso, gestionamos el usuario en nuestra BD
        Optional<Usuario> usuarioExistente = usuarioRepository.findByRut(rutCompleto);

        String passwordEncriptada = encryptionService.encrypt(password);

        if (usuarioExistente.isPresent()) {
            // El usuario ya existe, actualizamos su contraseña si ha cambiado.
            Usuario usuario = usuarioExistente.get();
            if (!usuario.getPasswordEncriptada().equals(passwordEncriptada)) {
                usuario.setPasswordEncriptada(passwordEncriptada);
                usuarioRepository.save(usuario);
                System.out.println("Contraseña actualizada para el usuario: " + rutCompleto);
            }
        } else {
            // El usuario es nuevo, lo creamos.
            // Necesitamos scrapear su nombre de la página de inicio.
            Document dashboardDoc = loginResponse.parse(); // Jsoup parsea el HTML de la respuesta
            String nombreUsuario = dashboardDoc.select("a[href*='perfil_egreso'] + a").text().trim();
            if (nombreUsuario.isEmpty()) {
                nombreUsuario = "Usuario Desconocido"; // Valor por defecto
            }

            Usuario nuevoUsuario = new Usuario(rutCompleto, nombreUsuario, passwordEncriptada);
            usuarioRepository.save(nuevoUsuario);
            System.out.println("Nuevo usuario creado: " + rutCompleto);
        }

        // --- FIN DE LA NUEVA LÓGICA DE BASE DE DATOS ---

        // 4. Devolvemos la respuesta de éxito al controlador
        return Map.of(
                "success", true,
                "sessionId", sessionId
        );
    }

    // Metodo que toma el sessionId obtenido y realiza una petición GET para obtener un HTML con la
    // lista de ramos.
    public String getAsignaturasHtml(String sessionId) throws IOException {
        // 1. Generamos nuestro propio "cache buster" con el timestamp actual
        long timestamp = System.currentTimeMillis();

        // 2. Construimos la URL completa y correcta
        String asignaturasUrl = INTRANET_ROOT_URL + sessionId + "/calificaciones/ver_calif_show.php?_=" + timestamp;
        System.out.println("DEBUG: Obteniendo asignaturas desde: " + asignaturasUrl);

        // 3. Hacemos la petición GET. No necesitamos una sesión nueva, podemos usar Jsoup directamente.
        //    Es importante enviar el User-Agent para parecer un navegador.
        Connection.Response response = Jsoup.connect(asignaturasUrl)
                .userAgent(USER_AGENT)
                .method(Connection.Method.GET)
                .execute();

        // 4. Verificamos que la petición fue exitosa
        if (response.statusCode() == 200) {
            // Devolvemos el cuerpo de la respuesta, que es el HTML con la tabla de asignaturas
            return response.body();
        } else {
            throw new IOException("No se pudo obtener la lista de asignaturas. Código de estado: " + response.statusCode());
        }
    }

    // METODO 1: Obtener la lista de carreras (AHORA CON LA LÓGICA CORRECTA)
    public List<CarreraDto> getAvailableCareers(String sessionId) throws IOException {
        Document doc = getInitialAsignaturasPage(sessionId);

        // Usamos el selector CSS que descubriste
        return doc.select("select[name=cambio_carrera] option").stream()
                .map(option -> {
                    String nombre = option.text();
                    String valorCompleto = option.val();
                    String[] partes = valorCompleto.split("-");

                    // Creamos el DTO con todos los datos desglosados
                    return new CarreraDto(
                            nombre,
                            valorCompleto,
                            partes[0], // crr_codigo
                            partes[1], // pca_codigo
                            partes[2], // alc_ano_ingreso
                            partes[3]  // alc_periodo
                    );
                })
                .collect(Collectors.toList());
    }

    // METODO 2: Obtener las asignaturas para una carrera específica (AHORA CON EL PAYLOAD CORRECTO)
// Necesitamos pasar el DTO completo para tener todos los datos.
    public String getAsignaturasForCareer(String sessionId, CarreraDto carrera) throws IOException {
        Document doc = getInitialAsignaturasPage(sessionId);

        String postUrl = INTRANET_ROOT_URL + sessionId + "/calificaciones/ver_calif_show.php";

        // Extraemos los datos que son comunes a todas las peticiones
        String aluRut = doc.select("input[name=alu_rut]").val();
        String anio = doc.select("input[name=anio]").val();
        String periodo = obtenerPeriodoActual(sessionId);

        // Hacemos la petición POST con el payload que descifraste
        Connection.Response response = Jsoup.connect(postUrl)
                .userAgent(USER_AGENT)
                .method(Connection.Method.POST)
                .data("volver", "volver")
                .data("url_volver", "")
                .data("alu_rut", aluRut)
                .data("crr_codigo", carrera.crrCodigo()) // Usamos los datos del DTO
                .data("pca_codigo", carrera.pcaCodigo())
                .data("alc_ano_ingreso", carrera.alcAnoIngreso())
                .data("alc_periodo", carrera.alcPeriodo())
                .data("anio", anio)
                .data("periodo", periodo)
                .data("cambio_carrera", carrera.valorCompleto()) // El valor completo del option
                .execute();

        return response.body();
    }

    // --- INICIO DEL CÓDIGO QUE FALTABA ---

    /**
     * Metodo ayudante para obtener el documento HTML de la página de asignaturas.
     * Lo hacemos privado porque solo será usado por otros métodos dentro de esta clase.
     * @param sessionId El ID de sesión válido.
     * @return El documento HTML parseado por Jsoup.
     * @throws IOException Si la petición falla.
     */
    private Document getInitialAsignaturasPage(String sessionId) throws IOException {
        long timestamp = System.currentTimeMillis();
        String asignaturasUrl = INTRANET_ROOT_URL + sessionId + "/calificaciones/ver_calif_show.php?_=" + timestamp;

        return Jsoup.connect(asignaturasUrl)
                .userAgent(USER_AGENT)
                .get();
    }

    // --- FIN DEL CÓDIGO QUE FALTABA ---

    public DashboardDataDto getDashboardData(String sessionId) throws IOException {
        // 1. Obtenemos la página inicial de asignaturas, que contiene toda la info.
        Document doc = getInitialAsignaturasPage(sessionId);

        // 2. Extraemos el nombre y apellido del usuario.
        // Buscamos la etiqueta <label> que está justo después de la que contiene "Nombres"
        String nombres = doc.select("label.blue:contains(Nombres) + label").text().trim();
        String apellidos = doc.select("label.blue:contains(Apellidos) + label").text().trim();
        String nombreCompleto = nombres + " " + apellidos;

        // 3. Extraemos la lista de carreras (reutilizamos la lógica que ya teníamos).
        List<CarreraDto> carreras = doc.select("select[name=cambio_carrera] option").stream()
                .map(option -> {
                    String nombre = option.text();
                    String valorCompleto = option.val();
                    String[] partes = valorCompleto.split("-");
                    return new CarreraDto(nombre, valorCompleto, partes[0], partes[1], partes[2], partes[3]);
                })
                .collect(Collectors.toList());

        // 4. Devolvemos everything empaquetado en nuestro nuevo DTO.
        return new DashboardDataDto(nombreCompleto, carreras);
    }

    // METODO PRINCIPAL MODIFICADO: Ahora calcula el promedio para cada asignatura
    public List<AsignaturaDto> getParsedAsignaturas(String sessionId, CarreraDto carrera) throws IOException {
        String html = getAsignaturasForCareer(sessionId, carrera);
        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("table.table.table-hover tbody tr");

        List<AsignaturaDto> asignaturasConPromedio = new java.util.ArrayList<>();

        // El contador ahora es simplemente el índice del bucle
        for (int i = 0; i < rows.size(); i++) {
            Element row = rows.get(i);
            String nombreAsignatura = row.select("td:first-child").text();
            if (nombreAsignatura.isEmpty()) continue;

            Element califButton = row.select("span[title='Ver Calificaciones']").first();
            if (califButton == null) {
                asignaturasConPromedio.add(new AsignaturaDto(nombreAsignatura, 0.0));
                continue;
            }

            String onClickAttr = califButton.attr("onClick");

            try {
                // --- INICIO DE LA CORRECCIÓN ---
                // Pasamos el índice del bucle 'i' directamente como el valor de 'det'.
                double promedio = getAsignaturaPromedio(sessionId, onClickAttr, doc, i);
                // --- FIN DE LA CORRECCIÓN ---

                asignaturasConPromedio.add(new AsignaturaDto(nombreAsignatura, promedio));

            } catch (Exception e) {
                System.err.println("Error calculando promedio para " + nombreAsignatura + ": " + e.getMessage());
                asignaturasConPromedio.add(new AsignaturaDto(nombreAsignatura, 0.0));
            }
        }
        return asignaturasConPromedio;
    }

    // Modificamos la firma para quitar el parámetro de cookies
    private double getAsignaturaPromedio(String sessionId, String onClickAttr, Document mainPageDoc, int detIndex) throws IOException {
        boolean isModular = onClickAttr.contains("ver_calificacion_modular");
        // Ya no pasamos las cookies
        Document notasDoc = getNotasHtml(sessionId, onClickAttr, mainPageDoc, detIndex);

        if (isModular) {
            return calculatePromedioModular(notasDoc);
        } else {
            return calculatePromedioNormal(notasDoc);
        }
    }

    // Modificamos la firma para quitar el parámetro de cookies
    private Document getNotasHtml(String sessionId, String onClickAttr, Document mainPageDoc, int detIndex) throws IOException {
        String paramsString = onClickAttr.substring(onClickAttr.indexOf('(') + 1, onClickAttr.lastIndexOf(')'));
        String[] params = paramsString.split(",\\s*");
        String endpoint;
        Connection connection;

        if (onClickAttr.contains("abrir_CalifShow")) {
            endpoint = "remote_ver_calif_greybox.php";
            String aluRut = params[0];
            String agnCodigo = params[1].replaceAll("\\D+", "");
            String ano = params[2];
            String periodo = params[3];
            String seccion = params[4];
            String nombre = params[5].replace("'", "").trim();

            String notasUrl = INTRANET_ROOT_URL + sessionId + "/calificaciones/" + endpoint +
                    "?det=" + detIndex +
                    "&alu_rut=" + aluRut +
                    "&agn_codigo=" + agnCodigo +
                    "&ano=" + ano +
                    "&periodo=" + periodo +
                    "&seccion=" + seccion +
                    "&nombre=" + java.net.URLEncoder.encode(nombre, java.nio.charset.StandardCharsets.UTF_8);

            connection = Jsoup.connect(notasUrl);
        } else {
            endpoint = "detalle_calificacion_modular.php";
            String aluRut = mainPageDoc.select("input[name=alu_rut]").val();
            String crrCodigo = mainPageDoc.select("input[name=crr_codigo]").val();
            String pcaCodigo = mainPageDoc.select("input[name=pca_codigo]").val();
            String alcAnoIngreso = mainPageDoc.select("input[name=alc_ano_ingreso]").val();
            String alcPeriodo = mainPageDoc.select("input[name=alc_periodo]").val();

            String asig = params[0].replaceAll("\\D+", "");
            String sec = params[1];
            String agnio = params[2];
            String per = params[3];

            String notasUrl = INTRANET_ROOT_URL + sessionId + "/calificaciones_escala/" + endpoint;
            connection = Jsoup.connect(notasUrl)
                    .data("agn_codigo", asig, "seccion", sec, "ano", agnio, "periodo", per, "alu_rut", aluRut, "crr_codigo", crrCodigo, "pca_codigo", pcaCodigo, "alc_ano_ingreso", alcAnoIngreso, "alc_periodo", alcPeriodo);
        }

        // La sesión principal se maneja a nivel de controlador, no necesitamos pasar cookies aquí
        return connection.userAgent(USER_AGENT).post();
    }

    private double calculatePromedioNormal(Document notasDoc) {
        // --- PASO 1 Y 2 (SIN CAMBIOS) ---
        // La lógica para construir la estructura de Evaluacion y asignar las notas
        // es la misma que en la respuesta anterior.
        List<Evaluacion> evaluaciones = new ArrayList<>();
        Elements ponderacionRows = notasDoc.select("h3:contains(Ponderaciones) ~ div table tbody tr");
        Evaluacion evaluacionPadreActual = null;
        for (Element row : ponderacionRows) {
            Elements cells = row.select("td");
            if (row.select("i.fa-book").size() > 0) {
                String nombre = cells.get(0).text().trim();
                int factor = Integer.parseInt(cells.get(2).text().trim());
                evaluacionPadreActual = new Evaluacion(nombre, factor);
                evaluaciones.add(evaluacionPadreActual);
            } else if (row.select("i.fa-chevron-right").size() > 0 && evaluacionPadreActual != null) {
                String nombreSub = cells.get(0).text().trim();
                int factorSub = Integer.parseInt(cells.get(2).text().trim());
                evaluacionPadreActual.subEvaluaciones.add(new SubEvaluacion(nombreSub, factorSub));
            }
        }

        Elements notasCells = notasDoc.select("h3:contains(Calificaciones) ~ div table tbody tr.Tabla3 td");
        int notaIndex = 0;
        for (Evaluacion eval : evaluaciones) {
            if (notaIndex >= notasCells.size()) break;
            if (eval.subEvaluaciones.isEmpty()) {
                String textoNota = notasCells.get(notaIndex).text().trim();
                if (!textoNota.isEmpty()) {
                    try { eval.notaFinal = Double.parseDouble(textoNota.replace(',', '.')); } catch (Exception e) {}
                }
                notaIndex++;
            } else {
                for (SubEvaluacion sub : eval.subEvaluaciones) {
                    if (notaIndex >= notasCells.size()) break;
                    String textoNota = notasCells.get(notaIndex).text().trim();
                    if (!textoNota.isEmpty()) {
                        try { sub.nota = Double.parseDouble(textoNota.replace(',', '.')); } catch (Exception e) {}
                    }
                    notaIndex++;
                }
                eval.calcularNotaFinal();
            }
        }

        // --- PASO 3: CALCULAR LA NOTA FINAL PONDERADA (LÓGICA CORREGIDA) ---
        double sumaDeAportes = 0;

        System.out.println("\n--- DEBUG: CÁLCULO DE NOTA FINAL PROYECTADA ---");
        for (Evaluacion eval : evaluaciones) {
            if (eval.factorTotal > 0 && eval.notaFinal > 0) {
                // El "aporte" de cada evaluación es su nota por su ponderación (factor)
                double aporte = eval.notaFinal * eval.factorTotal;
                sumaDeAportes += aporte;
                System.out.println(String.format("  -> Aporte de '%s': Nota %.2f * Factor %d = %.2f", eval.nombre, eval.notaFinal, eval.factorTotal, aporte));
            }
        }

        // --- ¡AQUÍ ESTÁ EL CAMBIO CLAVE! ---
        // El promedio final es la suma de los aportes, dividido por 100.
        double promedioFinal = sumaDeAportes / 100.0;

        System.out.println("Suma de Aportes (Nota * Factor): " + sumaDeAportes);
        System.out.println("Cálculo Final: " + sumaDeAportes + " / 100.0 = " + promedioFinal);
        System.out.println("--- FIN DEBUG ---\n");

        return promedioFinal;
    }

    private double calculatePromedioModular(Document notasDoc) {
        // 1. Encontrar el último módulo activo
        Elements modulos = notasDoc.select("h3:contains(Calificaciones MÓDULO)");
        Element ultimoModuloActivo = null;
        for (int i = modulos.size() - 1; i >= 0; i--) {
            Element moduloHeader = modulos.get(i);
            Element tablaContainer = moduloHeader.nextElementSibling();
            if (tablaContainer != null && !tablaContainer.select("tbody tr").isEmpty()) {
                ultimoModuloActivo = tablaContainer;
                break;
            }
        }

        if (ultimoModuloActivo == null) return 0.0;

        System.out.println("\n--- DEBUG: Iniciando cálculo para RAMO MODULAR ---");
        String moduloTitle = ultimoModuloActivo.previousElementSibling().text();
        System.out.println("Calculando para el módulo: " + moduloTitle);

        // --- LÓGICA JERÁRQUICA APLICADA A MODULARES ---

        // PASO 1: CONSTRUIR LA ESTRUCTURA DE EVALUACIONES
        // En los modulares, toda la info está en una sola tabla.
        List<Evaluacion> evaluaciones = new ArrayList<>();
        Evaluacion evaluacionPadreActual = null;

        for (Element row : ultimoModuloActivo.select("tbody tr")) {
            Elements cells = row.select("td");
            if (cells.size() == 4) { // Es una evaluación principal
                try {
                    String nombre = cells.get(0).text().trim();
                    int factor = Integer.parseInt(cells.get(2).text().trim());
                    double nota = Double.parseDouble(cells.get(3).text().trim().replace(',', '.'));

                    evaluacionPadreActual = new Evaluacion(nombre, factor);
                    evaluacionPadreActual.notaFinal = nota; // Asignamos la nota directamente
                    evaluaciones.add(evaluacionPadreActual);

                } catch (NumberFormatException e) { /* Ignorar filas de pie de tabla */ }
            }
            // Aquí tendríamos que añadir la lógica si los modulares tuvieran sub-evaluaciones.
            // Por ahora, el HTML que hemos visto no tiene ese caso, pero el código está listo para expandirse.
        }

        // PASO 2: CALCULAR LA NOTA FINAL PONDERADA
        double sumaDeAportes = 0;
        double sumaDeFactoresUsados = 0;

        System.out.println("\n--- DEBUG: CÁLCULO DE NOTA FINAL MODULAR ---");
        for (Evaluacion eval : evaluaciones) {
            if (eval.factorTotal > 0 && eval.notaFinal > 0) {
                double aporte = eval.notaFinal * eval.factorTotal;
                sumaDeAportes += aporte;
                sumaDeFactoresUsados += eval.factorTotal;
                System.out.println(String.format("  -> Aporte de '%s': Nota %.2f * Factor %d = %.2f", eval.nombre, eval.notaFinal, eval.factorTotal, aporte));
            }
        }

        // El promedio de un módulo SÍ se calcula sobre los factores cursados en ESE módulo.
        double promedioFinal = (sumaDeFactoresUsados > 0) ? (sumaDeAportes / sumaDeFactoresUsados) : 0.0;

        System.out.println("Suma de Aportes: " + sumaDeAportes);
        System.out.println("Suma de Factores Usados: " + sumaDeFactoresUsados);
        System.out.println("Cálculo Final: " + sumaDeAportes + " / " + sumaDeFactoresUsados + " = " + promedioFinal);
        System.out.println("--- FIN DEBUG ---\n");

        return promedioFinal;
    }

    public String obtenerPeriodoActual(String sessionId) throws IOException {
        Document doc = obtenerPaginaPeriodo(sessionId);// busca el input cuyo id es periodo_acad

        return doc.select("input#periodo_acad").attr("value");
    }

    private Document obtenerPaginaPeriodo(String sessionId) throws IOException {
        String url = INTRANET_ROOT_URL + sessionId + "/alumnos/consulta_solicitud_retiro_temporal.php";
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .get();
    }
}