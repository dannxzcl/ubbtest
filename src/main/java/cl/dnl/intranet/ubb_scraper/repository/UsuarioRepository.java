package cl.dnl.intranet.ubb_scraper.repository;

import cl.dnl.intranet.ubb_scraper.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository // Le dice a Spring que esta es una interfaz para acceder a datos.
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca un usuario por su RUT.
     * Spring Data JPA entiende el nombre de este método y automáticamente
     * crea la consulta SQL "SELECT * FROM usuarios WHERE rut = ?".
     *
     * @param rut El RUT del usuario a buscar.
     * @return Un Optional que puede contener al Usuario si se encuentra, o estar vacío si no.
     */
    Optional<Usuario> findByRut(String rut);

}