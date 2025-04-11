module com.mediaproducer {
    requires org.slf4j;

    opens com.mediaproducer to javafx.fxml;
    exports com.mediaproducer;
}