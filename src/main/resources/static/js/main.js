document.addEventListener('DOMContentLoaded', () => {
    const urlParams = new URLSearchParams(window.location.search);
    const sessionId = urlParams.get('sessionId');

    const welcomeTextElement = document.getElementById('welcome-text');
    const careerContainer = document.getElementById('career-selector-container');
    const mainContent = document.querySelector('.main-content');

    let carrerasDisponibles = [];

    async function loadRamos(carreraSeleccionada) {
        mainContent.innerHTML = '<p>Cargando asignaturas...</p>';
        try {
            const response = await fetch(`http://localhost:8080/api/asignaturas/parsed/${sessionId}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(carreraSeleccionada)
            });

            if (!response.ok) throw new Error('No se pudieron cargar las asignaturas.');

            const asignaturas = await response.json();

            if (asignaturas.length > 0) {
                let gridHtml = '<div class="ramos-grid">';
                
                asignaturas.forEach(asignatura => {
                    // 1. Determinar la clase de estado según el promedio
                    const notaDeCorte = 3.95;
                    let estadoClase = '';
                    if (asignatura.promedio > 0) { // Solo aplicar color si hay un promedio calculado
                        estadoClase = asignatura.promedio >= notaDeCorte ? 'ramo-aprobando' : 'ramo-reprobando';
                    }

                    // 2. Construir el HTML del cuadro
                    // Añadimos la clase de estado al div principal.
                    // El texto del promedio ahora está dentro de su propio span.
                    gridHtml += `<div class="ramo-box ${estadoClase}">
                                    <span class="ramo-nombre">${asignatura.nombre}</span>
                                    <span class="ramo-promedio">Promedio: ${asignatura.promedio.toFixed(1)}</span>
                                </div>`;
                });
                gridHtml += '</div>';
                mainContent.innerHTML = gridHtml;
            } else {
                mainContent.innerHTML = '<p>No se encontraron asignaturas para la carrera y período seleccionados.</p>';
            }

        } catch (error) {
            console.error('Error:', error);
            mainContent.innerHTML = `<p style="color: red;">${error.message}</p>`;
        }
    }
    
    async function loadDashboard() {
        try {
            const response = await fetch(`http://localhost:8080/api/dashboard/${sessionId}`);
            if (!response.ok) throw new Error('No se pudieron cargar los datos del dashboard.');
            const data = await response.json();

            welcomeTextElement.textContent = `¡Bienvenido, ${data.nombreUsuario}!`;
            
            carrerasDisponibles = data.carreras;

            if (carrerasDisponibles && carrerasDisponibles.length > 0) {
                let selectHtml = '<select id="career-select">';
                carrerasDisponibles.forEach(carrera => {
                    selectHtml += `<option value="${carrera.crrCodigo}">${carrera.nombre}</option>`;
                });
                selectHtml += '</select>';
                careerContainer.innerHTML = selectHtml;

                const careerSelect = document.getElementById('career-select');
                careerSelect.addEventListener('change', (event) => {
                    const selectedCarreraCodigo = event.target.value;
                    const carrera = carrerasDisponibles.find(c => c.crrCodigo === selectedCarreraCodigo);
                    if (carrera) {
                        loadRamos(carrera);
                    }
                });

                loadRamos(carrerasDisponibles[0]);

            } else {
                careerContainer.innerHTML = 'No se encontraron carreras.';
            }

        } catch (error) {
            console.error('Error:', error);
            welcomeTextElement.textContent = error.message;
        }
    }

    loadDashboard();
});