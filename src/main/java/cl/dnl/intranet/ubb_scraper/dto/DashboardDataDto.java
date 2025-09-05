package cl.dnl.intranet.ubb_scraper.dto;

import java.util.List;

public record DashboardDataDto(
        String nombreUsuario,
        List<CarreraDto> carreras
) {}