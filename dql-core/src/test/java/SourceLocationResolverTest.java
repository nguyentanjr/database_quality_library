import org.example.callsite.SourceLocationConfig;
import org.example.callsite.SourceLocationResolver;
import org.junit.jupiter.api.Test;

import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceLocationResolverTest {

    @Test
    void skips_framework_prefixes_and_runtime_proxy_markers() {
        Optional<String> sourceLocation = SourceLocationResolver.resolve(Stream.of(
                frame("org.springframework.jdbc.core.JdbcTemplate", "query", "JdbcTemplate.java", 710),
                frame("io.demo.entity.User$HibernateProxy$xxo6c9Rz", "getOrders", "User.java", 1),
                frame("io.demo.VetServiceImpl", "toDto", "VetServiceImpl.java", 85)
        ), SourceLocationConfig.defaults());

        assertEquals(
                Optional.of("io.demo.VetServiceImpl.toDto(VetServiceImpl.java:85)"),
                sourceLocation
        );
    }

    @Test
    void honors_extra_internal_prefixes() {
        Optional<String> sourceLocation = SourceLocationResolver.resolve(Stream.of(
                frame("com.acme.generated.DaoHelper", "run", "DaoHelper.java", 12),
                frame("com.acme.clinic.VetServiceImpl", "toDto", "VetServiceImpl.java", 85)
        ), new SourceLocationConfig(true, List.of(), List.of("com.acme.generated."), 256));

        assertEquals(
                Optional.of("com.acme.clinic.VetServiceImpl.toDto(VetServiceImpl.java:85)"),
                sourceLocation
        );
    }

    @Test
    void disabled_config_returns_empty() {
        Optional<String> sourceLocation = SourceLocationResolver.resolve(Stream.of(
                frame("com.acme.VetServiceImpl", "toDto", "VetServiceImpl.java", 85)
        ), new SourceLocationConfig(false, List.of(), List.of(), 256));

        assertTrue(sourceLocation.isEmpty());
    }

    @Test
    void application_prefix_wins_over_internal_framework_prefix() {
        Optional<String> sourceLocation = SourceLocationResolver.resolve(Stream.of(
                frame("org.springframework.jdbc.core.JdbcTemplate", "query", "JdbcTemplate.java", 710),
                frame("org.springframework.samples.petclinic.dqldemo.DqlDemoController", "nPlusOne", "DqlDemoController.java", 44)
        ), new SourceLocationConfig(
                true,
                List.of("org.springframework.samples.petclinic."),
                List.of(),
                256
        ));

        assertEquals(
                Optional.of("org.springframework.samples.petclinic.dqldemo.DqlDemoController.nPlusOne(DqlDemoController.java:44)"),
                sourceLocation
        );
    }

    @Test
    void application_prefix_does_not_unhide_runtime_proxy_markers() {
        Optional<String> sourceLocation = SourceLocationResolver.resolve(Stream.of(
                frame("io.demo.entity.User$HibernateProxy$xxo6c9Rz", "getOrders", "User.java", 1),
                frame("io.demo.VetServiceImpl", "toDto", "VetServiceImpl.java", 85)
        ), new SourceLocationConfig(true, List.of("io.demo."), List.of(), 256));

        assertEquals(
                Optional.of("io.demo.VetServiceImpl.toDto(VetServiceImpl.java:85)"),
                sourceLocation
        );
    }

    @Test
    void all_internal_frames_return_empty() {
        Optional<String> sourceLocation = SourceLocationResolver.resolve(Stream.of(
                frame("org.springframework.jdbc.core.JdbcTemplate", "query", "JdbcTemplate.java", 710),
                frame("org.hibernate.engine.jdbc.spi.SqlStatementLogger", "logStatement", "SqlStatementLogger.java", 144),
                frame("net.ttddyy.dsproxy.listener.ChainListener", "afterQuery", "ChainListener.java", 36)
        ), SourceLocationConfig.defaults());

        assertTrue(sourceLocation.isEmpty());
    }

    @Test
    void business_frame_outside_max_depth_returns_empty() {
        Optional<String> sourceLocation = SourceLocationResolver.resolve(Stream.of(
                frame("org.springframework.jdbc.core.JdbcTemplate", "query", "JdbcTemplate.java", 710),
                frame("org.hibernate.engine.jdbc.spi.SqlStatementLogger", "logStatement", "SqlStatementLogger.java", 144),
                frame("com.acme.VetServiceImpl", "toDto", "VetServiceImpl.java", 85)
        ), new SourceLocationConfig(true, List.of(), List.of(), 2));

        assertTrue(sourceLocation.isEmpty());
    }

    private StackFrame frame(String className, String methodName, String fileName, int lineNumber) {
        return new TestStackFrame(className, methodName, fileName, lineNumber);
    }

    private record TestStackFrame(
            String className,
            String methodName,
            String fileName,
            int lineNumber
    ) implements StackFrame {
        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public String getMethodName() {
            return methodName;
        }

        @Override
        public Class<?> getDeclaringClass() {
            return Object.class;
        }

        @Override
        public int getByteCodeIndex() {
            return -1;
        }

        @Override
        public String getFileName() {
            return fileName;
        }

        @Override
        public int getLineNumber() {
            return lineNumber;
        }

        @Override
        public boolean isNativeMethod() {
            return false;
        }

        @Override
        public StackTraceElement toStackTraceElement() {
            return new StackTraceElement(className, methodName, fileName, lineNumber);
        }
    }
}
