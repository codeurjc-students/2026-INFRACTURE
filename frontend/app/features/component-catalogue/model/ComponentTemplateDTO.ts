import type { ComponentConfigurationDTO } from "./ComponentConfigurationDTO";
import type { TemplateContractDTO } from "./TemplateContractDTO";

// A catalogue template: what it is, its contract and the configuration every new node copies.
export interface ComponentTemplateDTO {
  key: string;
  name: string;
  type: string;
  contract: TemplateContractDTO;
  initialConfiguration: ComponentConfigurationDTO;
}
