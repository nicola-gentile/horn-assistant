package hornassistant.cli.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RendererTest {

    private static final Doc DOC = Doc.concat(
            Doc.styled(Role.ERROR_LABEL, "error:"),
            Doc.text(" "),
            new Doc.Styled(Role.HEADING, Doc.concat(Doc.text("a "), Doc.styled(Role.OPTION, "--b"))),
            Doc.line(),
            Doc.styled(Role.PARAMETER, ""),
            Doc.text("tail"));

    private static String strip(String text) {
        return text.replaceAll("\u001b\\[[0-9;]*m", "");
    }

    @Test
    void plainRendererDropsRoles() {
        assertThat(new PlainRenderer().render(DOC)).isEqualTo("error: a --b\ntail");
    }

    @Test
    void tamboRendererStylesWithoutChangingText() {
        String styled = new TamboRenderer().render(DOC);
        assertThat(styled).contains("\u001b[");
        assertThat(strip(styled)).isEqualTo(new PlainRenderer().render(DOC));
    }

    @Test
    void tamboRendererLeavesUnstyledTextBare() {
        assertThat(new TamboRenderer().render(Doc.concat(Doc.text("plain"), Doc.line()))).isEqualTo("plain\n");
    }

    @Test
    void nestedRolesCombineTheirStyles() {
        String styled = new TamboRenderer().render(new Doc.Styled(Role.HEADING, Doc.styled(Role.OPTION, "x")));
        // HEADING is bold (1), OPTION is cyan (36): the inner span carries both.
        assertThat(styled).matches("\u001b\\[0(;[0-9]+)*m(x)\u001b\\[0m").contains(";1").contains("36");
    }

    @Test
    void everyRoleHasAStyle() {
        for (Role role : Role.values()) {
            String styled = new TamboRenderer().render(Doc.styled(role, "x"));
            assertThat(styled).as("%s", role).contains("\u001b[");
        }
    }

    @Test
    void forStreamChoosesTheRenderer() {
        assertThat(Renderer.forStream(true)).isInstanceOf(TamboRenderer.class);
        assertThat(Renderer.forStream(false)).isInstanceOf(PlainRenderer.class);
    }

    @Test
    void textMayNotContainLineBreaks() {
        assertThatThrownBy(() -> Doc.text("a\nb")).isInstanceOf(IllegalArgumentException.class);
    }
}
