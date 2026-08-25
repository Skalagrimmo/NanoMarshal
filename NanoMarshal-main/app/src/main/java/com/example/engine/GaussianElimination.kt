package com.example.engine

import kotlin.math.abs

/**
 * Numerical linear algebra solver utilizing Gaussian Elimination with partial pivoting.
 * Used for solving system of linear equations Ax = b in render math, Voronoi bisector intersections,
 * perspective transformation matrix fits, surface normal plane fits, and polynomial light fitting.
 */
class GaussianElimination {

    companion object {
        private const val EPSILON = 1e-10

        /**
         * Solves the linear system Ax = b using Gaussian Elimination with Partial Pivoting.
         * @param matrixA N x N coefficient matrix
         * @param vectorB N x 1 constant vector
         * @return DoubleArray containing solution vector x, or null if system is singular/unsolvable.
         */
        fun solve(matrixA: Array<DoubleArray>, vectorB: DoubleArray): DoubleArray? {
            val n = vectorB.size
            if (matrixA.size != n) return null

            // Augmented matrix [A | b]
            val aug = Array(n) { i ->
                DoubleArray(n + 1) { j ->
                    if (j < n) matrixA[i][j] else vectorB[i]
                }
            }

            // Forward Elimination with Partial Pivoting
            for (col in 0 until n) {
                // Find pivot row with maximum absolute value in current column
                var pivotRow = col
                var maxVal = abs(aug[col][col])
                for (row in col + 1 until n) {
                    val absVal = abs(aug[row][col])
                    if (absVal > maxVal) {
                        maxVal = absVal
                        pivotRow = row
                    }
                }

                // Check for singular matrix
                if (maxVal < EPSILON) return null

                // Swap pivot row with current row
                if (pivotRow != col) {
                    val tempRow = aug[col]
                    aug[col] = aug[pivotRow]
                    aug[pivotRow] = tempRow
                }

                // Eliminate entries below pivot
                for (row in col + 1 until n) {
                    val factor = aug[row][col] / aug[col][col]
                    for (j in col until n + 1) {
                        aug[row][j] -= factor * aug[col][j]
                    }
                }
            }

            // Back Substitution
            val x = DoubleArray(n)
            for (row in n - 1 downTo 0) {
                var sum = 0.0
                for (j in row + 1 until n) {
                    sum += aug[row][j] * x[j]
                }
                x[row] = (aug[row][n] - sum) / aug[row][row]
            }

            return x
        }

        /**
         * Calculates the determinant of an N x N matrix via Gaussian elimination.
         */
        fun determinant(matrix: Array<DoubleArray>): Double {
            val n = matrix.size
            val a = Array(n) { i -> matrix[i].clone() }
            var det = 1.0

            for (col in 0 until n) {
                var pivotRow = col
                for (row in col + 1 until n) {
                    if (abs(a[row][col]) > abs(a[pivotRow][col])) {
                        pivotRow = row
                    }
                }

                if (abs(a[pivotRow][col]) < EPSILON) return 0.0

                if (pivotRow != col) {
                    val temp = a[col]
                    a[col] = a[pivotRow]
                    a[pivotRow] = temp
                    det *= -1.0
                }

                det *= a[col][col]

                for (row in col + 1 until n) {
                    val factor = a[row][col] / a[col][col]
                    for (j in col + 1 until n) {
                        a[row][j] -= factor * a[col][j]
                    }
                }
            }

            return det
        }

        /**
         * Solves for a 2D plane equation z = a*x + b*y + c given 3 non-collinear 3D points.
         * Used for Voronoi cell surface normal fitting and height interpolation.
         * Returns DoubleArray [a, b, c] or null if degenerate.
         */
        fun fitPlane3D(
            p1x: Double, p1y: Double, p1z: Double,
            p2x: Double, p2y: Double, p2z: Double,
            p3x: Double, p3y: Double, p3z: Double
        ): DoubleArray? {
            val matrixA = arrayOf(
                doubleArrayOf(p1x, p1y, 1.0),
                doubleArrayOf(p2x, p2y, 1.0),
                doubleArrayOf(p3x, p3y, 1.0)
            )
            val vectorB = doubleArrayOf(p1z, p2z, p3z)
            return solve(matrixA, vectorB)
        }

        /**
         * Solves 2x2 line intersection equation A*x = b for two 2D line equations:
         * Line 1: a1*x + b1*y = c1
         * Line 2: a2*x + b2*y = c2
         * Used for exact Voronoi cell vertex intersection calculations.
         */
        fun solve2DIntersection(
            a1: Double, b1: Double, c1: Double,
            a2: Double, b2: Double, c2: Double
        ): DoubleArray? {
            val matrixA = arrayOf(
                doubleArrayOf(a1, b1),
                doubleArrayOf(a2, b2)
            )
            val vectorB = doubleArrayOf(c1, c2)
            return solve(matrixA, vectorB)
        }
    }
}
