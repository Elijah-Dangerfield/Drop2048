plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.sharing"
}

// No dependencies, deliberately — not on :libraries:cascade, not on
// :libraries:progress. A share is a string built from four numbers, and the
// module that builds it must not be able to see a board, a seed or a transcript
// it could accidentally print. A share that leaked a board state would be
// handing the reader a run they have not played.
