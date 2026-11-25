# R8 rules for the release and benchmark builds (S15P31E102-1120).
# Library rules already cover kotlinx.serialization, coroutines, Ktor, Koin, AndroidX and Play app-update;
# add a rule here only for code of ours that R8 cannot see (reflection, JNI, names read at runtime).
# Decisions and measurements: docs/knowledge/frontend/android-r8-release-build.md

# Keep line numbers so Play Console can retrace crash stacks with the mapping.txt bundled in the AAB.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
