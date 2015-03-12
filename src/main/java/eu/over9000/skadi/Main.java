package eu.over9000.skadi;

import javafx.application.Application;
import eu.over9000.skadi.lock.SingleInstanceLock;
import eu.over9000.skadi.ui.MainWindow;

public class Main {
	
	public static void main(final String[] args) throws Exception {
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
		
		if (!SingleInstanceLock.startSocketLock()) {
			System.err.println("another instance is up");
			return;
		}
		
		Application.launch(MainWindow.class, args);

		SingleInstanceLock.stopSocketLock();

		System.exit(0);
	}
	
}