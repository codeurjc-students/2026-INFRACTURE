package es.codeurjc.infracture.catalog.application;

import java.util.List;

public final class ContractCatalogs {

    private ContractCatalogs() {
    }

    public static ContractCatalog standard() {
        return new ContractCatalog(List.of(new HttpServiceDefinition(), new WorkerDefinition(),
                new PostgresqlDefinition(), new RedisDefinition(), new RabbitmqDefinition()));
    }
}
