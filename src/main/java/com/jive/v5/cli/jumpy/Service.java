package com.jive.v5.cli.jumpy;

import java.util.Map;

import lombok.Value;

@Value
public class Service
{
  private String name;
  private Map<String, String> properties;
}
