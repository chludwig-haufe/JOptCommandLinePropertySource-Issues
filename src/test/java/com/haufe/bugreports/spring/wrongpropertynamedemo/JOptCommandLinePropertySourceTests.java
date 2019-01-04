package com.haufe.bugreports.spring.wrongpropertynamedemo;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.core.env.JOptCommandLinePropertySource;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.arrayContainingInAnyOrder;

/**
 * JUnit test class that demonstrates the inconsistent behavior of
 * {@link JOptCommandLinePropertySource#getPropertyNames()}
 */
public class JOptCommandLinePropertySourceTests {

    private static final String SHORT_CHARSET_OPTION_NAME = "c";
    private static final String LONG_CHARSET_OPTION_NAME = "charset";
    private static final String QUALIFIED_CHARSET_OPTION_NAME = "myapp.output-charset";
    private static final List<String> CHARSET_OPTION_ALIAS_NAMES =
            Arrays.asList(SHORT_CHARSET_OPTION_NAME, LONG_CHARSET_OPTION_NAME, QUALIFIED_CHARSET_OPTION_NAME);

    private static final String SHORT_THREADS_OPTION_NAME = "t";
    private static final String LONG_THREADS_OPTION_NAME = "threads";
    private static final String QUALIFIED_THREADS_OPTION_NAME = "myapp.max-thread-pool-size";
    private static final List<String> THREAD_OPTION_ALIAS_NAMES =
            Arrays.asList(SHORT_THREADS_OPTION_NAME, LONG_THREADS_OPTION_NAME, QUALIFIED_THREADS_OPTION_NAME);

    private static final String[] ARGUMENTS = {"-c", "UTF-8", "--threads=4"};

    private OptionSet parseArguments(String[] arguments) {
        OptionParser optionParser = new OptionParser(false);
        optionParser.acceptsAll(CHARSET_OPTION_ALIAS_NAMES).withRequiredArg();
        optionParser.acceptsAll(Arrays.asList("h", "help")).forHelp();
        optionParser.acceptsAll(THREAD_OPTION_ALIAS_NAMES);
        return optionParser.parse(arguments);
    }

    /**
     * This test fails because {@link JOptCommandLinePropertySource#getPropertyNames()} selects the
     * option alias names in a not very useful way. (The documentation does not specify how the property names
     * are determined if a JOpt {@link OptionSpec} maps to more than one option name,
     * so one can argue whether this constitutes a bug or not.)
     * <p>
     * A comment in the implementation of {@link JOptCommandLinePropertySource#getPropertyNames()}
     * claims only the <em>longest</em> name is used for enumerating. However, the implementation always returns
     * the <em>last</em> element of {@link OptionSpec#options()}.
     * This is, in general, <em>not</em> the longest option name, because {@link OptionSpec#options()} first
     * clusters the option names into short and long options and
     * then sorts these groups lexicographically, respectively.
     * <p>
     * Therefore, the {@code propertySource.getPropertyNames()} returns the names {@link #QUALIFIED_CHARSET_OPTION_NAME}
     * and {@link #LONG_THREADS_OPTION_NAME}.
     */
    @Ignore
    @Test
    public void testThatJOptCommandLinePropertySourceListsLongestOptionAlias() {
        OptionSet optionSet = parseArguments(ARGUMENTS);
        JOptCommandLinePropertySource propertySource = new JOptCommandLinePropertySource(optionSet);

        Assert.assertThat(propertySource.getPropertyNames(),
                arrayContainingInAnyOrder(QUALIFIED_CHARSET_OPTION_NAME, QUALIFIED_THREADS_OPTION_NAME));
    }

    /**
     * Test that demonstrates that {@code propertySource.getPropertyNames()} returns the qualified option name aliases
     * if the implementation is changed such that in fact always the (or rather: a) longest alias name is selected.
     * <p>
     * <b>Note:</b> It's possible to construct examples where an unqualified long option name is longer
     * than a qualified alias. This might be rare in practice, but it cannot be ruled out.
     */
    @Test
    public void testThatJOptCommandLinePropertySourceWithLengthFixListsLongestOptionAlias() {
        Comparator<String> stringLengthComparator = Comparator.comparingInt(String::length);
        Function<List<String>, Stream<String>> lengthFixAliasSelector = aliasList ->
                aliasList.stream()
                        .max(stringLengthComparator)  // Optional<String>
                        .map(Stream::of)              // Optional<Stream<String>> (stream count is 1)
                        .orElseGet(Stream::empty);    // Stream<String> (stream count is 0 or 1)

        OptionSet optionSet = parseArguments(ARGUMENTS);
        JOptCommandLinePropertySource propertySource =
                new JOptCommandLinePropertySourceWithAliases(optionSet, lengthFixAliasSelector);

        Assert.assertThat(propertySource.getPropertyNames(),
                arrayContainingInAnyOrder(QUALIFIED_CHARSET_OPTION_NAME, QUALIFIED_THREADS_OPTION_NAME));
    }

    /**
     * Test that demonstrates that {@code propertySource.getPropertyNames()} returns the qualified option name aliases
     * if the implementation is changed such that the alias names are selected that contain a {@code '.'}.
     * <p>
     * <b>Note:</b> A JOpt parser can, of course, be configured to accept multiple qualified alias names for an option.
     * The implementation here would return <em>all</em> qualified alias names. I don't see why this should cause
     * any issues when building the application context.
     */
    @Test
    public void testThatJOptCommandLinePropertySourceWithDotHeuristicFixListsQualifiedPropertyNames() {
        Function<List<String>, Stream<String>> qualifiedNameSelector = aliasList ->
                aliasList.stream()
                        .filter(name -> name.contains("."));
        OptionSet optionSet = parseArguments(ARGUMENTS);
        JOptCommandLinePropertySource propertySource =
                new JOptCommandLinePropertySourceWithAliases(optionSet, qualifiedNameSelector);

        Assert.assertThat(propertySource.getPropertyNames(),
                arrayContainingInAnyOrder(QUALIFIED_CHARSET_OPTION_NAME, QUALIFIED_THREADS_OPTION_NAME));
    }

    /**
     * Test that takes the observation from
     * {@link #testThatJOptCommandLinePropertySourceWithDotHeuristicFixListsQualifiedPropertyNames()}
     * to the extreme and makes {@code propertySource.getPropertyNames()} return <em>all</em> option alias name,
     * no matter whether it's a short, a long, or a "qualified" long option name.
     */
    @Test
    public void testThatJOptCommandLinePropertySourceWithAllAliasesFixListsAllOptionAliases() {
        Function<List<String>, Stream<String>> allAliasesSelector = Collection::stream;
        OptionSet optionSet = parseArguments(ARGUMENTS);
        JOptCommandLinePropertySource propertySource =
                new JOptCommandLinePropertySourceWithAliases(optionSet, allAliasesSelector);

        String[] expectedPropertyNames = Stream.of(CHARSET_OPTION_ALIAS_NAMES, THREAD_OPTION_ALIAS_NAMES)
                .flatMap(Collection::stream)
                .toArray(String[]::new);
        Assert.assertThat(propertySource.getPropertyNames(), arrayContainingInAnyOrder(expectedPropertyNames));
    }

    /**
     * Version of {@link JOptCommandLinePropertySource} whose {@link #getPropertyNames()} method delegates
     * selection of the property names from the {@link OptionSpec#options() spec's option name list} to
     * a function object.
     */
    public static class JOptCommandLinePropertySourceWithAliases extends JOptCommandLinePropertySource {

        private final Function<List<String>, Stream<String>> aliasFilter;

        /**
         * Construct a JOpt command line property source that enumerates the property names determined by the
         * specified function object.
         *
         * @param options     the parsed arguments that back this JOpt command line property source
         * @param aliasFilter the function object that determines which option alias names are treated as property names
         */
        public JOptCommandLinePropertySourceWithAliases(OptionSet options,
                                                        Function<List<String>, Stream<String>> aliasFilter) {
            super(options);
            this.aliasFilter = Objects.requireNonNull(aliasFilter, "aliasFilter must not be null");
        }

        /**
         * Construct an array that for each option in {@link #getSource()} contains the alias names returned
         * by {@link #aliasFilter}.
         *
         * @return the names of the enumerated properties
         */
        @Override
        public String[] getPropertyNames() {
            return getSource().specs().stream()
                    .flatMap(spec -> aliasFilter.apply(spec.options()))
                    .toArray(String[]::new);
        }
    }


}

