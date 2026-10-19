# NanoMarshal Go Core

This module is the first incremental extraction of platform-independent logic from NanoMarshal. It starts with the grid A* search used by the existing Kotlin VoxelPathfinder. It does not replace the renderer, Compose UI, save system, voxel world construction, or the rest of the game engine.

Run tests from this directory with: go test ./... and go test -race ./...

From the repository root, use bash scripts/build-go-core.sh to generate app/libs/nanomarshalcore.aar for Android ARM and ARM64. The script expects ANDROID_HOME and ANDROID_NDK_HOME to point to an installed Android SDK and NDK.

The Android Gradle build includes the AAR only when the file exists. Until the AAR is generated, the existing Kotlin A* implementation remains a fallback. At runtime, the Kotlin bridge tries the generated Go binding and falls back safely if it is absent or a binding call fails.

The API nanocore.FindPath(width, height, startX, startY, targetX, targetY, costs) accepts a row-major float32 cost grid. Positive values are traversable cell costs; zero, negative, NaN, and infinite values are blocked. The result is a flattened sequence of integer coordinate pairs: x0,y0,x1,y1,...

Traversal penalties remain calculated by Kotlin for now to preserve current game rules while moving the search algorithm itself to Go.
