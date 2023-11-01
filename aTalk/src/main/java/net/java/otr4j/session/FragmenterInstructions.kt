package net.java.otr4j.session

/**
 * Instructions for the fragmenter explaining how to fragment a payload.
 *
 * @author Danny van Heumen
 * @author Eng Chong Meng
 */
class FragmenterInstructions
/**
 * Constructor.
 *
 * @param maxFragmentsAllowed Maximum fragments allowed.
 * @param maxFragmentSize Maximum fragment size allowed.
 */
(
        /**
         * Maximum number of fragments.
         */
        val maxFragmentsAllowed: Int,
        /**
         * Maximum size for fragments.
         */
        val maxFragmentSize: Int) {
    companion object {
        /**
         * Constant for indicating an unlimited amount.
         */
        const val UNLIMITED = -1

        /**
         * Verify instructions for safe usage. It will also create a default instructions instance in case null is provided.
         *
         * If an invalid number is specified, it will be replaced with UNLIMITED.
         *
         * @param instructions the instructions or null for defaults
         * @return returns instructions.
         */
        fun verify(instructions: FragmenterInstructions?): FragmenterInstructions {
            if (instructions == null) {
                return FragmenterInstructions(UNLIMITED, UNLIMITED)
            }
            require(!(instructions.maxFragmentsAllowed != UNLIMITED
                    && instructions.maxFragmentsAllowed < 0)) { "Invalid fragmenter instructions: bad number of fragments." }
            require(!(instructions.maxFragmentSize != UNLIMITED
                    && instructions.maxFragmentSize < 0)) { "Invalid fragmenter instructions: bad fragment size." }
            return instructions
        }
    }
}