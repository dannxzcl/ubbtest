package cl.dnl.intranet.ubb_scraper.dto;

// Este DTO ahora almacena todos los datos que necesitamos de cada carrera
public record CarreraDto(
        String nombre,
        String valorCompleto, // ej: "29040-2-2021-1"
        String crrCodigo,     // ej: "29040"
        String pcaCodigo,     // ej: "2"
        String alcAnoIngreso, // ej: "2021"
        String alcPeriodo     // ej: "1"
) {}