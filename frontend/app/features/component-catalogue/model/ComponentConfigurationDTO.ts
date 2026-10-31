// A profile and its integer parameters, such as a template's initial configuration.
export interface ComponentConfigurationDTO {
  profile: string;
  parameters: Record<string, number>;
}
