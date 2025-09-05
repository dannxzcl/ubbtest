package cl.dnl.intranet.ubb_scraper.controller;

import cl.dnl.intranet.ubb_scraper.dto.CarreraDto;
import cl.dnl.intranet.ubb_scraper.dto.LoginRequest;
import cl.dnl.intranet.ubb_scraper.service.UbbScrapingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.io.IOException;

import cl.dnl.intranet.ubb_scraper.dto.DashboardDataDto;
import cl.dnl.intranet.ubb_scraper.dto.AsignaturaDto;

@RestController // Indica que esta clase es un controlador REST. Cada metodo devolverá un objeto como
                // JSON automáticamente.
@RequestMapping("/api") // Prefijo común para todas las rutas de este controlador. En este caso, todas
                        // las rutas comienzan con /api
@CrossOrigin(origins = "*") // Permite solicitudes desde cualquier origen (útil para desarrollo; en
                            // producción mejor restringirlo.
public class ScraperController {
    private final UbbScrapingService scrapingService; // Se inyecta una instancia de UbbScrapingService,
                                                     // que contiene la lógica de autenticación y
                                                    // scraping.

    public ScraperController(UbbScrapingService scrapingService) { // Se usa una inyección por constructor.
        this.scrapingService = scrapingService;
    }

    // Metodo principal handleLogin()
    @PostMapping("/login")
    // Escucha las solicitudes POST enviadas a /api/login
    // El cuerpo de la solicitud se mapea automáticamente al DTO LoginRequest
    public ResponseEntity<Map<String, Object>> handleLogin(@RequestBody LoginRequest loginRequest) {
        try {
            Map<String, Object> result = scrapingService.performLogin(loginRequest.rut(), loginRequest.password());
            boolean isSuccess = (boolean) result.get("success");

            if (isSuccess) { // Si el login fue exitoso, devuelve un código 200 con un mensaje de éxito
                            // y un sessionId
                return ResponseEntity.ok(Map.of(
                        "message", "Login correcto!",
                        "sessionId", result.get("sessionId")
                ));
            } else {
                // Si falló, pasamos toda la información de depuración al frontend.
                return ResponseEntity.status(401).body(Map.of(
                        "message", "Login incorrecto",
                        "details", result // 'result' contiene toda la info de debug
                ));
            }
        } catch (Exception e) { // Si ocurre un error inesperado se captura cualquier excepción,
                                // imprimiendo el error en consola y respondiendo con un código 500
                                // (error del servidor).
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Error interno del servidor: " + e.getMessage()));
        }
    }

    // Nuevo endpoint para que el frontend pueda pedir la información
    @GetMapping("/asignaturas/{sessionId}")
    public ResponseEntity<String> getAsignaturas(@PathVariable String sessionId) {
        try {
            // Llamamos a nuestro nuevo metodo en el servicio
            String asignaturasHtml = scrapingService.getAsignaturasHtml(sessionId);

            // Devolvemos el HTML directamente con un status 200 OK
            return ResponseEntity.ok(asignaturasHtml);

        } catch (IOException e) {
            e.printStackTrace();
            // Devolvemos un mensaje de error
            return ResponseEntity.status(500).body("Error al obtener las asignaturas: " + e.getMessage());
        }
    }

    // Endpoint para obtener la lista de carreras
    @GetMapping("/carreras/{sessionId}")
    public ResponseEntity<List<CarreraDto>> getCarreras(@PathVariable String sessionId) {
        try {
            List<CarreraDto> carreras = scrapingService.getAvailableCareers(sessionId);
            return ResponseEntity.ok(carreras);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    // Endpoint para obtener las asignaturas de una carrera específica
    @PostMapping("/asignaturas/{sessionId}")
    public ResponseEntity<String> getAsignaturas(@PathVariable String sessionId, @RequestBody CarreraDto carrera) {
        try {
            String asignaturasHtml = scrapingService.getAsignaturasForCareer(sessionId, carrera);
            return ResponseEntity.ok(asignaturasHtml);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error al obtener las asignaturas: " + e.getMessage());
        }
    }

    // Endpoint para obtener todos los datos iniciales del dashboard
    @GetMapping("/dashboard/{sessionId}")
    public ResponseEntity<DashboardDataDto> getDashboardData(@PathVariable String sessionId) {
        try {
            DashboardDataDto dashboardData = scrapingService.getDashboardData(sessionId);
            return ResponseEntity.ok(dashboardData);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    // Endpoint para devolver la lista de asignaturas parseada en formato JSON
    @PostMapping("/asignaturas/parsed/{sessionId}")
    public ResponseEntity<List<AsignaturaDto>> getParsedAsignaturas(@PathVariable String sessionId, @RequestBody CarreraDto carrera) {
        try {
            List<AsignaturaDto> asignaturas = scrapingService.getParsedAsignaturas(sessionId, carrera);
            return ResponseEntity.ok(asignaturas);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}