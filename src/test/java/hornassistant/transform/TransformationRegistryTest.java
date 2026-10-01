package hornassistant.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hornassistant.system.ChcSystem;
import org.junit.jupiter.api.Test;

class TransformationRegistryTest {

    private record Named(String name) implements Transformation {
        @Override
        public String help() {
            return "does nothing";
        }

        @Override
        public void transform(ChcSystem system) {}
    }

    @Test
    void standardRegistryHasNormAndItIsTheDefault() {
        var registry = TransformationRegistry.standard();
        assertThat(registry.all()).extracting(Transformation::name).containsExactly("norm");
        assertThat(registry.lookup("norm")).isPresent();
        assertThat(registry.lookup("nope")).isEmpty();
        assertThat(TransformationRegistry.DEFAULT_LIST).containsExactly("norm");
        assertThat(registry.lookup("norm").orElseThrow().help()).isNotBlank();
    }

    @Test
    void keepsRegistrationOrder() {
        var registry = TransformationRegistry.empty().register(new Named("b")).register(new Named("a"));
        assertThat(registry.all()).extracting(Transformation::name).containsExactly("b", "a");
    }

    @Test
    void rejectsDuplicateNames() {
        var registry = TransformationRegistry.empty().register(new Named("x"));
        assertThatThrownBy(() -> registry.register(new Named("x"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registeringDoesNotChangeTheOriginal() {
        var registry = TransformationRegistry.empty();
        registry.register(new Named("x"));
        assertThat(registry.all()).isEmpty();
    }
}
