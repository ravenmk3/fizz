package ravenworks.fizz.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
@AllArgsConstructor
public class ServiceListItemResponse {

    private String serviceName;
    private List<ServiceInstanceDto> instances;


    @Getter
    @Setter
    @AllArgsConstructor
    public static class ServiceInstanceDto {

        private String id;
        private String scheme;
        private String host;
        private int port;

    }

}
