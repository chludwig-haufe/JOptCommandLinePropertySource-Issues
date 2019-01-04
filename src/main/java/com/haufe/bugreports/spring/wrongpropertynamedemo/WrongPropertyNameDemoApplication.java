package com.haufe.bugreports.spring.wrongpropertynamedemo;

import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@SpringBootApplication
public class WrongPropertyNameDemoApplication implements ApplicationRunner {

    private static final JOptConfig JOPT_CONFIG = new JOptConfig();

    private final Environment springEnvironment;
    private final EnumerablePropertySource<?> commandLinePropertySource;

    public WrongPropertyNameDemoApplication(ConfigurableEnvironment springEnvironment) {
        this.springEnvironment = springEnvironment;
        PropertySource<?> propertySource = springEnvironment.getPropertySources()
                .get(JOptCommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME);
        if (!(propertySource instanceof EnumerablePropertySource)) {
            throw new IllegalArgumentException("spring environment does not contain a command line property source");
        }
        commandLinePropertySource = (EnumerablePropertySource) propertySource;
    }

    @Override
    public void run(ApplicationArguments args) {
        JOPT_CONFIG.getOptionSpecs().stream()
                .filter(spec -> !(spec instanceof NonOptionArgumentSpec))
                .flatMap(spec -> spec.options().stream())
                .forEach(optionName -> {
                    System.out.printf("Property %s=%s%n",
                            optionName,
                            springEnvironment.getProperty("c", "<not found>"));
                });
        System.out.printf("All properties in property source \"%s\":%n",
                JOptCommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME);
        Stream.of(commandLinePropertySource.getPropertyNames())
                .forEach(propName ->
                        System.out.printf("  %s=%s%n", propName, commandLinePropertySource.getProperty(propName)));
    }

    public static void main(String[] args) {
        OptionSet parsedArguments = JOPT_CONFIG.parse(args);
        JOptCommandLinePropertySource commandLinePropertySource = new JOptCommandLinePropertySource(parsedArguments);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(commandLinePropertySource);
        SpringApplication app = new SpringApplication(WrongPropertyNameDemoApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setEnvironment(environment);
        app.run(JOPT_CONFIG.nonOptionArguments(parsedArguments));
    }

    private static class JOptConfig {
        private static final List<String> CHARSET_OPTION_ALIAS_NAMES =
                Arrays.asList("c", "output-charset", "myapp.output-charset");

        private final OptionParser optionParser;
        private final NonOptionArgumentSpec<String> nonOptionArgumentSpec;

        JOptConfig() {
            optionParser = new OptionParser(false);
            optionParser.acceptsAll(CHARSET_OPTION_ALIAS_NAMES).withRequiredArg();
            nonOptionArgumentSpec = optionParser.nonOptions("unrecognized options and arguments");
        }

        OptionSet parse(String[] args) {
            return optionParser.parse(args);
        }

        String[] nonOptionArguments(OptionSet optionSet) {
            return optionSet.valuesOf(nonOptionArgumentSpec).toArray(new String[0]);
        }

        Set<OptionSpec<?>> getOptionSpecs() {
            return new HashSet<>(optionParser.recognizedOptions().values());
        }
    }
}

