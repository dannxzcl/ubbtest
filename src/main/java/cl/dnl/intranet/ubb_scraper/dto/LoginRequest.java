package cl.dnl.intranet.ubb_scraper.dto;

// Usando un 'record', Java genera automáticamente los métodos rut() y password()
// que son equivalentes a getRut() y getPassword().
public record LoginRequest(String rut, String password) {
}