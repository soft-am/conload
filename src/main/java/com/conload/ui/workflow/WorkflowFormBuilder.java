package com.conload.ui.workflow;

import com.conload.ui.components.UiFactory;
import com.conload.workflow.WorkflowFieldDefinition;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builds a dynamic form from a list of {@link WorkflowFieldDefinition}s.
 * Text fields and toggle checkboxes are both handled.
 * Returns a map of field-key -> string value via a supplier.
 */
final class WorkflowFormBuilder {

    private WorkflowFormBuilder() {}

    record FormResult(VBox container, Supplier<Map<String, String>> valuesSupplier) {}

    static FormResult build(List<WorkflowFieldDefinition> fields, Map<String, String> prefillValues) {
        VBox container = new VBox(10);
        container.setPadding(new Insets(8, 0, 8, 0));
        Map<String, Supplier<String>> suppliers = new LinkedHashMap<>();

        for (WorkflowFieldDefinition field : fields) {
            if (field.toggle()) {
                buildToggleField(field, prefillValues, container, suppliers);
            } else {
                buildTextField(field, prefillValues, container, suppliers);
            }
        }

        return new FormResult(container, () -> collectValues(suppliers));
    }

    private static void buildToggleField(WorkflowFieldDefinition field, Map<String, String> prefill,
                                          VBox container, Map<String, Supplier<String>> suppliers) {
        boolean defaultSelected = !prefill.containsKey(field.key())
                || !"false".equalsIgnoreCase(prefill.get(field.key()));
        CheckBox checkBox = new CheckBox(field.label());
        checkBox.setSelected(defaultSelected);
        checkBox.getStyleClass().add("workflow-toggle");
        container.getChildren().add(checkBox);
        suppliers.put(field.key(), () -> String.valueOf(checkBox.isSelected()));
    }

    private static void buildTextField(WorkflowFieldDefinition field, Map<String, String> prefill,
                                        VBox container, Map<String, Supplier<String>> suppliers) {
        VBox fieldBox = new VBox(4);
        Label label = UiFactory.fieldLabel(field.label());
        if (field.required()) label.setText(label.getText() + " *");

        TextInputControl input = field.multiline()
                ? createTextArea()
                : UiFactory.darkTextField(field.prompt());

        String prefillVal = prefill != null ? prefill.get(field.key()) : null;
        if (prefillVal != null && !prefillVal.isEmpty()) input.setText(prefillVal);

        suppliers.put(field.key(), () -> input.getText());
        fieldBox.getChildren().addAll(label, input);
        container.getChildren().add(fieldBox);
    }

    private static TextArea createTextArea() {
        TextArea ta = new TextArea();
        ta.setWrapText(true);
        ta.setPrefRowCount(3);
        ta.getStyleClass().addAll("input", "text-area");
        return ta;
    }

    private static Map<String, String> collectValues(Map<String, Supplier<String>> suppliers) {
        Map<String, String> values = new LinkedHashMap<>();
        for (var entry : suppliers.entrySet()) {
            values.put(entry.getKey(), entry.getValue().get());
        }
        return values;
    }
}
