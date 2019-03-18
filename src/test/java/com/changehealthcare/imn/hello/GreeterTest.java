package com.changehealthcare.imn.hello;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GreeterTest {

  @ParameterizedTest
  @CsvSource(value = {"Ryan:Hello, Ryan.", ":Hello, null."}, delimiter = ':')
  void greetTest(String input, String expected) {
    String greeting = Greeter.greet(input);

    assertEquals(expected, greeting, "Actual greeting did not match expected greeting.");
  }
}