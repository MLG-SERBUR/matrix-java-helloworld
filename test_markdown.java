import com.fasterxml.jackson.databind.ObjectMapper;

public class test_markdown {
    public static void main(String[] args) {
        String testMarkdown = """
            # Header 1
            ## Header 2
            This is **bold** and *italic* text.
            Inline `code` example.
            [Link to Matrix](https://matrix.org)
            
            > This is a blockquote
            - List item 1
            - List item 2
            
            ```java
            public class Test {
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
            ```
            """;
        
        System.out.println("Original markdown:");
        System.out.println(testMarkdown);
        System.out.println("\nConverted HTML:");
        System.out.println(convertMarkdownToHtml(testMarkdown));
    }
    
    private static String convertMarkdownToHtml(String markdown) {
        // Simple markdown to HTML conversion
        String html = markdown;
        
        // Convert headers (# Header) - handle them line by line
        String[] lines = html.split("\n");
        StringBuilder result = new StringBuilder();
        
        for (String line : lines) {
            if (line.matches("^#\\s+.*")) {
                result.append("<h1>").append(line.replaceFirst("^#\\s+", "")).append("</h1>");
            } else if (line.matches("^##\\s+.*")) {
                result.append("<h2>").append(line.replaceFirst("^##\\s+", "")).append("</h2>");
            } else if (line.matches("^###\\s+.*")) {
                result.append("<h3>").append(line.replaceFirst("^###\\s+", "")).append("</h3>");
            } else if (line.matches("^####\\s+.*")) {
                result.append("<h4>").append(line.replaceFirst("^####\\s+", "")).append("</h4>");
            } else if (line.matches("^#####\\s+.*")) {
                result.append("<h5>").append(line.replaceFirst("^#####\\s+", "")).append("</h5>");
            } else if (line.matches("^######\\s+.*")) {
                result.append("<h6>").append(line.replaceFirst("^######\\s+", "")).append("</h6>");
            } else if (line.matches("^>\\s+.*")) {
                result.append("<blockquote>").append(line.replaceFirst("^>\\s+", "")).append("</blockquote>");
            } else if (line.matches("^[-*]{3,}\\s*$")) {
                result.append("<hr>");
            } else if (line.matches("^-\\s+.*")) {
                result.append("<li>").append(line.replaceFirst("^-\\s+", "")).append("</li>");
            } else if (line.matches("^\\d+\\.\\s+.*")) {
                result.append("<li>").append(line.replaceFirst("^\\d+\\.\\s+", "")).append("</li>");
            } else {
                result.append(line);
            }
            result.append("\n");
        }
        
        html = result.toString();
        
        // Convert bold (**text**)
        html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>");
        
        // Convert italic (*text*)
        html = html.replaceAll("\\*(.*?)\\*", "<em>$1</em>");
        
        // Convert inline code (`code`)
        html = html.replaceAll("`([^`]+)`", "<code>$1</code>");
        
        // Convert links ([text](url))
        html = html.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
        
        // Convert code blocks (```language\ncontent\n```)
        html = html.replaceAll("```\\w*\n(.*?)\n```", "<pre><code>$1</code></pre>");
        
        // Wrap lists in <ul> tags
        html = html.replaceAll("(<li>.*?</li>)+", "<ul>$0</ul>");
        
        // Convert newlines to <br> tags (but preserve HTML tags)
        html = html.replaceAll("\n", "<br>");
        
        return html;
    }
}