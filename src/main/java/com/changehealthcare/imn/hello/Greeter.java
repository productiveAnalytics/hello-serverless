package com.changehealthcare.imn.hello;

public class Greeter {

  private Greeter() {}

  /**
   * Takes a message and returns a greeting.
   *
   * @param message message to wrap with a greeting
   * @return a greeting in the form of 'Hello, message.'
   */
  public static String greet(String message) {

    return String.format("Hello, %s.", message);
  }
}
