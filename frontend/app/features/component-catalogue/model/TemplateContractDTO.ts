// What a node type accepts: its profiles, each with parameters, outgoing ports and offered capabilities.
export interface TemplateContractDTO {
  version: number;
  profiles: {
    name: string;
    parameters: { name: string; min: number; max: number }[];
    // A port connects to nodes that offer one of the capabilities it accepts.
    ports: {
      name: string;
      label: string;
      accepts: string[];
      min: number;
      max: number;
    }[];
    capabilities: string[];
  }[];
}
