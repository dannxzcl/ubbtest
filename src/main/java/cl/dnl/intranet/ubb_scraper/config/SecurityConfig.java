package cl.dnl.intranet.ubb_scraper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

// Ya no necesitamos el import estático para H2
// import static org.springframework.boot.autoconfigure.security.servlet.PathRequest.toH2Console;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // --- INICIO DE LA CORRECCIÓN ---
                        // Eliminamos la línea .requestMatchers(toH2Console()).permitAll()
                        // y mantenemos solo las reglas que necesitamos.
                        .requestMatchers("/api/**", "/", "/*.html", "/css/**", "/js/**").permitAll()
                        // --- FIN DE LA CORRECCIÓN ---

                        .anyRequest().authenticated()
                )

                .formLogin(form -> form.permitAll())

                // La configuración de headers para H2 ya no es necesaria, pero no hace daño dejarla.
                // La eliminamos para mayor limpieza.
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));

        return http.build();
    }
}