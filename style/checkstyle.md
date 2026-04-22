# PMD to Checkstyle Rules Conversion

This document summarizes the conversion of Alibaba Java Coding Guidelines PMD rules (`com.alibaba.p3c:p3c-pmd`) to standard Maven Checkstyle rules. The configuration is integrated into `style/checkstyle.xml`.

## Naming Conventions (`ali-naming.xml`)

| PMD Rule | Checkstyle Equivalent | Description |
|---|---|---|
| `ClassNamingShouldBeCamelRule` | `TypeName` | Validates that class names follow UpperCamelCase. |
| `AbstractClassShouldStartWithAbstractNamingRule` | `AbstractClassName` | Validates that abstract classes start with `Abstract` or `Base` (`ignoreModifier="true"`). |
| `ConstantFieldShouldBeUpperCaseRule` | `ConstantName` | Validates that constants are in UPPER_SNAKE_CASE. |
| `LowerCamelCaseVariableNamingRule` | `MemberName`, `LocalVariableName`, `ParameterName`, `MethodName` | Validates that variables and methods use lowerCamelCase. |
| `PackageNamingRule` | `PackageName` | Validates package naming conventions (all lowercase). |
| `ArrayNamingShouldHaveBracketRule` | `ArrayTypeStyle` | Validates array declarations to use Java style (`String[] args`). |

## Flow Control (`ali-flowcontrol.xml`)

| PMD Rule | Checkstyle Equivalent | Description |
|---|---|---|
| `SwitchStatementRule` | `FallThrough` | Checks for switch statement fall-through and missing `default` blocks. |
| `NeedBracesRule` | `NeedBraces`, `EmptyBlock` | Enforces the use of braces for `if`, `else`, `for`, `while`, and `do` blocks, and validates empty blocks. |

## OOP (`ali-oop.xml`)

| PMD Rule | Checkstyle Equivalent | Description |
|---|---|---|
| `EqualsAvoidNullRule` | `EqualsAvoidNull` | Checks that `equals()` is called on known non-null objects to avoid `NullPointerException`. |

## Constants (`ali-constant.xml`)

| PMD Rule | Checkstyle Equivalent | Description |
|---|---|---|
| `UpperEllRule` | `UpperEll` | Enforces the use of an uppercase `L` for `long` literals (e.g., `1L` instead of `1l`). |

## Unsupported or Partially Supported Rules
Some PMD rules perform deeper semantic analysis that standard Checkstyle does not support directly without custom extensions. These include:
- `BooleanPropertyShouldNotStartWithIsRule`
- `ExceptionClassShouldEndWithExceptionRule`
- Specific Alibaba comment format requirements (`ali-comment.xml`) are not fully 1-to-1 matchable without custom Checks.
- Thread pool and concurrent rules (`ali-concurrent.xml`).
- Transaction and ORM rules (`ali-orm.xml`, `ali-exception.xml`).
