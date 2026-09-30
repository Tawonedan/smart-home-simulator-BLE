package UI;

import javafx.application.Application;

/**
 * Punto de entrada de la aplicación.
 *
 * Existe porque la JVM rechaza arrancar directamente una subclase de {@link Application}
 * cuando JavaFX está en el classpath y no en el module-path (caso de los scripts de
 * distribución y de los JAR ejecutables). Lanzarla desde una clase normal evita el error
 * "JavaFX runtime components are missing".
 */
public final class Launcher {

	private Launcher() {
	}

	public static void main(String[] args) {
		Application.launch(VisualGridApp.class, args);
	}
}
