module com.mku.salmon.vault.main {
    requires javafx.controls;
    requires javafx.fxml;
    requires salmon.core;
    requires salmon.fs;
    requires java.desktop;
    requires javafx.media;
    requires javafx.swing;
    requires java.prefs;
    requires salmon.win;

    opens com.mku.salmon.vault.controller to javafx.fxml;
    exports com.mku.salmon.vault.main;
    exports com.mku.salmon.vault.controller;
}