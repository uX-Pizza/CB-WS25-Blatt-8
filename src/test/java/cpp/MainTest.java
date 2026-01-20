package cpp;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import cpp.app.Main;
import org.junit.jupiter.api.Test;

class MainTest {
  @Test
  void appHasAGreeting() {
    Main classUnderTest = new Main();
    assertNotNull(classUnderTest);
  }
}
