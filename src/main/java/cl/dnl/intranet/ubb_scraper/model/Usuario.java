package cl.dnl.intranet.ubb_scraper.model;

import jakarta.persistence.*; // Usa jakarta.* para Spring Boot 3+, javax.* para Spring Boot 2

@Entity // Le dice a JPA que esta clase es una tabla en la base de datos.
@Table(name = "usuarios") // Opcional: especifica el nombre de la tabla (si no, sería "usuario").
public class Usuario {

    @Id // Marca este campo como la clave primaria (ID único).
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Le dice a la BD que genere el ID automáticamente.
    private Long id;

    @Column(unique = true, nullable = false) // Asegura que el RUT sea único y no pueda ser nulo.
    private String rut;

    @Column(nullable = false) // El nombre no puede ser nulo.
    private String nombre;

    @Column(length = 1024, nullable = false) // Columna para la contraseña, no nula y con longitud suficiente.
    private String passwordEncriptada;

    // --- Constructores ---

    // Constructor vacío requerido por JPA.
    public Usuario() {
    }

    // Constructor útil para crear nuevos usuarios.
    public Usuario(String rut, String nombre, String passwordEncriptada) {
        this.rut = rut;
        this.nombre = nombre;
        this.passwordEncriptada = passwordEncriptada;
    }

    // --- Getters y Setters ---
    // Necesarios para que JPA y otros frameworks puedan acceder a los campos.

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRut() {
        return rut;
    }

    public void setRut(String rut) {
        this.rut = rut;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getPasswordEncriptada() {
        return passwordEncriptada;
    }

    public void setPasswordEncriptada(String passwordEncriptada) {
        this.passwordEncriptada = passwordEncriptada;
    }
}