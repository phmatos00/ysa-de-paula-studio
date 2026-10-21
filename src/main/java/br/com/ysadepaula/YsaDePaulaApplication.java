package br.com.ysadepaula;

import br.com.ysadepaula.compartilhado.config.StudioProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(StudioProperties.class)
public class YsaDePaulaApplication {

    public static void main(String[] args) {
        SpringApplication.run(YsaDePaulaApplication.class, args);
    }
}
