package be.florien.joinorm.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import be.florien.joinorm.annotation.JoTable;

@SupportedAnnotationTypes("be.florien.joinorm.annotation.JoTable")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class DBTableProcessor extends AbstractProcessor{

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(JoTable.class)) {
        StringBuilder builder = new StringBuilder()
                .append("package be.florien.joinorm.generated;\n\n")
                .append("import be.florien.joinorm.architecture.DBTable;\n\n")
                .append("public class ").append(element.getSimpleName()).append("Table extends DBTable {\n")
                .append("}");

            try {
                JavaFileObject source = processingEnv.getFiler().createSourceFile("be.florien.joinorm.generated." + element.getSimpleName() + "Table");

                Writer writer = source.openWriter();
                writer.write(builder.toString());
                writer.flush();
                writer.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }
}
