package gazelle.ui;


import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class GazelleController extends BaseController {

    @FXML
    private VBox contentBox;

    private LogInController logInController;
    private CourseListController courseListController;

    @FXML
    private void initialize() throws IOException {
        logInController = LogInController.load();
        courseListController = CourseListController.load();

        setCurrentScreen(logInController);
    }

    private void setCurrentScreen(BaseController controller) {
        contentBox.getChildren().setAll(controller.getNode());
    }

    public void onClosing() {

    }

    public static GazelleController load() {
        return loadFromFXML("/scenes/main.fxml");
    }
}
