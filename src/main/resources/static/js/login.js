// Esperamos a que todo el contenido del HTML esté cargado
document.addEventListener('DOMContentLoaded', () => {
    
    const loginForm = document.getElementById('loginForm');
    const rutInput = document.getElementById('rut');
    const submitButton = loginForm.querySelector('button');

    // Formato de RUT
    function formatRut(rut) {
        const rutLimpio = rut.replace(/[^0-9kK]/g, ''); // Elimina todos los caracteres que no sean números (0-9) o letras (k/K)
        if (rutLimpio.length === 0) return ''; // Si después de limpiar el rut está vacío, la función retorna una cadena vacía.
        let cuerpo = rutLimpio.slice(0, -1); // Contiene todos los dígitos menos el último, que es el dígito verificador (dv)
        let dv = rutLimpio.slice(-1).toUpperCase(); // Dígito verificador
        cuerpo = cuerpo.replace(/\B(?=(\d{3})+(?!\d))/g, '.'); // Agrega puntos como separadores de miles al cuerpo del RUT
        return `${cuerpo}-${dv}`; // Devuelve el RUT formateado con puntos y guión.
    }

    rutInput.addEventListener('input', (event) => {
        event.target.value = formatRut(event.target.value); // Formatea automáticamente el RUT mientras el usuario escribe.
    });

    // Lógica de Login

    loginForm.addEventListener('submit', async function(event) {
        event.preventDefault();  // Se captura el evento "submit" del formulario para evitar que la página se recargue y, en su lugar,
                                // se maneja el login de forma asíncrona con JavaScript.

        const rut = document.getElementById('rut').value;
        const password = document.getElementById('password').value;

        const originalButtonText = submitButton.textContent;
        submitButton.textContent = 'Ingresando...'; // Muestra el texto "Ingresando..." en el botón
        submitButton.disabled = true;   // Desactiva la interacción con el botón para apretar clics múltiples

        try {
            const response = await fetch('http://localhost:8080/api/login', { // Petición HTTP POST a la API de login, enviando un
                                                                             // JSON con el rut y password.
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rut: rut, password: password }),
            });

            const data = await response.json(); // Procesamiento de la respuesta

            if (response.ok) { // Si el login fue exitoso (response.ok === true), se extrae el sessionId que devuelve la API
                              // y redirige al usuario al dashboard.html, pasando sessionId como parámetro en la URL.
                const sessionId = data.sessionId;
                window.location.href = `\main.html?sessionId=${sessionId}`;
            } else {
                // --- INICIO DE LA MODIFICACIÓN DE DEPURACIÓN ---
                // Construimos un mensaje de alerta detallado.
                let alertMessage = data.message;
                if (data.details) {
                    alertMessage += "\n\n--- DEBUG INFO ---\n";
                    alertMessage += `Status Code Recibido: ${data.details.debug_statusCode}\n`;
                    alertMessage += `Redirigido a: ${data.details.debug_location}\n`;
                    
                    // También mostramos el trozo de HTML en la consola del navegador para un análisis más profundo.
                    console.log("Cuerpo de la respuesta de error:", data.details.debug_bodySnippet);
                }
                alert(alertMessage);
            }

        } catch (error) { // Manejo de errores de red
            console.error('Error de conexión:', error);
            alert('No se pudo conectar con el servidor. Asegúrate de que el backend de Spring Boot esté corriendo.');
        } finally { // Finalmente, sin importar si el login fue exitoso o no, el botón se reactiva y vuelve a su estado original.
            submitButton.textContent = originalButtonText;
            submitButton.disabled = false;
        }
    });
});