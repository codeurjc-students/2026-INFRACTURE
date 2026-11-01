export interface TemplateContractDTO {
  version: number;
  profiles: {
    name: string;
    parameters: { name: string; min: number; max: number }[];
  }[];
}
