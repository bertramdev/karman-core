package com.bertramlabs.plugins.karman.exceptions

class ProviderNotFoundException extends Exception {
  public ProviderNotFoundException() {
    super('The Requested Provider was not found')
  } 

  public ProviderNotFoundException(String provider) {
    super("The Requested Provider was not found: ${provider}")
  } 
}