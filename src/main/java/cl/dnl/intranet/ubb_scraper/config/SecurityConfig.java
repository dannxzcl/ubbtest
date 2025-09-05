package cl.dnl.intranet.ubb_scraper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // Importante para deshabilitar CSRF
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.boot.autoconfigure.security.servlet.PathRequest.toH2Console; // Import estático para H2

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Deshabilitar CSRF usando la nueva sintaxis lambda
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Definir las reglas de autorización
                .authorizeHttpRequests(auth -> auth
                        // Permitir acceso a la consola de H2 de forma segura
                        .requestMatchers(toH2Console()).permitAll()

                        // Permitir acceso a todos nuestros endpoints de API y archivos de frontend
                        .requestMatchers("/api/**", "/", "/*.html", "/css/**", "/js/**").permitAll()

                        // Cualquier otra petición debe ser autenticada
                        .anyRequest().authenticated()
                )

                // 3. Configurar el formulario de login por defecto (no cambia mucho)
                .formLogin(form -> form.permitAll())

                // 4. Permitir que la consola H2 se muestre en un iframe
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())
                );

        return http.build();
    }
}