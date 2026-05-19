package ru.itis.documents.config.thymeleaf;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.templatemode.TemplateMode;
import org.unbescape.html.HtmlEscape;

public class CapricBadgeTagProcessor extends AbstractElementTagProcessor {

    private static final String TAG_NAME = "capric-badge";
    private static final int PRECEDENCE = 1000;

    public CapricBadgeTagProcessor(String dialectPrefix) {
        super(
                TemplateMode.HTML,
                dialectPrefix,
                TAG_NAME,
                true,
                null,
                false,
                PRECEDENCE
        );
    }

    @Override
    protected void doProcess(
            ITemplateContext context,
            IProcessableElementTag tag,
            IElementTagStructureHandler structureHandler
    ) {
        Object value = evaluate(context, tag.getAttributeValue("value"));

        String label = "Средняя";

        if (value instanceof CapriciousnessView cap
                && cap.label() != null
                && !cap.label().isBlank()) {
            label = cap.label();
        }

        String html = "<span class=\"badge capric-badge\">"
                + HtmlEscape.escapeHtml5(label)
                + "</span>";

        structureHandler.replaceWith(html, false);
    }

    private Object evaluate(ITemplateContext context, String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }

        IStandardExpressionParser parser =
                StandardExpressions.getExpressionParser(context.getConfiguration());

        return parser.parseExpression(context, expression).execute(context);
    }
}