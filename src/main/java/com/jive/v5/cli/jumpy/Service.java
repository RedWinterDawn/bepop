package com.jive.v5.cli.jumpy;

import java.util.Map;

import lombok.Value;

@Value
public class Service
{
  private final String name;
  private final Map<String, String> properties;
}
