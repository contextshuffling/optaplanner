/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.core.api.score.stream;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.junit.Assume;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.optaplanner.core.api.score.buildin.simple.SimpleScore;
import org.optaplanner.core.api.score.constraint.ConstraintMatch;
import org.optaplanner.core.api.score.constraint.ConstraintMatchTotal;
import org.optaplanner.core.impl.score.director.InnerScoreDirector;
import org.optaplanner.core.impl.score.director.stream.ConstraintStreamScoreDirectorFactory;
import org.optaplanner.core.impl.testdata.domain.score.lavish.TestdataLavishSolution;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public abstract class AbstractConstraintStreamTest {

    protected static final String TEST_CONSTRAINT_NAME = "testConstraintName";

    @Parameterized.Parameters(name = "constraintMatchEnabled={0}, constraintStreamImplType={1}")
    public static Object[][] data() {
        return new Object[][]{
                {false, ConstraintStreamImplType.BAVET},
                {true, ConstraintStreamImplType.BAVET},
                {false, ConstraintStreamImplType.DROOLS},
                {true, ConstraintStreamImplType.DROOLS}
        };
    }

    protected final boolean constraintMatchEnabled;
    protected final ConstraintStreamImplType constraintStreamImplType;

    protected void assumeBavet() {
        Assume.assumeTrue("This functionality is not yet supported in Drools-based constraint streams.",
                constraintStreamImplType == ConstraintStreamImplType.BAVET);
    }

    public AbstractConstraintStreamTest(boolean constraintMatchEnabled, ConstraintStreamImplType constraintStreamImplType) {
        this.constraintMatchEnabled = constraintMatchEnabled;
        this.constraintStreamImplType = constraintStreamImplType;
    }

    // ************************************************************************
    // SimpleScore creation and assertion methods
    // ************************************************************************

    protected InnerScoreDirector<TestdataLavishSolution> buildScoreDirector(Function<ConstraintFactory, Constraint> function) {
        ConstraintStreamScoreDirectorFactory<TestdataLavishSolution> scoreDirectorFactory
                = new ConstraintStreamScoreDirectorFactory<>(
                TestdataLavishSolution.buildSolutionDescriptor(),
                (constraintFactory) -> new Constraint[] {function.apply(constraintFactory)},
                constraintStreamImplType);
        return scoreDirectorFactory.buildScoreDirector(false, constraintMatchEnabled);
    }

    protected void assertScore(InnerScoreDirector<TestdataLavishSolution> scoreDirector,
            AssertableMatch... assertableMatches) {
        scoreDirector.triggerVariableListeners();
        SimpleScore score = (SimpleScore) scoreDirector.calculateScore();
        int scoreTotal = Arrays.stream(assertableMatches)
                .mapToInt(assertableMatch -> assertableMatch.score)
                .sum();
        if (constraintMatchEnabled) {
            String constraintPackage = scoreDirector.getSolutionDescriptor().getSolutionClass().getPackage().getName();
            ConstraintMatchTotal constraintMatchTotal = scoreDirector.getConstraintMatchTotalMap()
                    .get(ConstraintMatchTotal.composeConstraintId(constraintPackage, TEST_CONSTRAINT_NAME));
            for (AssertableMatch assertableMatch : assertableMatches) {
                if (constraintMatchTotal.getConstraintMatchSet().stream()
                        .noneMatch(constraintMatch -> assertableMatch.isEqualTo(constraintMatch))) {
                    fail("The assertableMatch (" + assertableMatch + ") is lacking,"
                            + " it's not in the constraintMatchSet ("
                            + constraintMatchTotal.getConstraintMatchSet() + ").");
                }
            }
            for (ConstraintMatch constraintMatch : constraintMatchTotal.getConstraintMatchSet()) {
                if (Arrays.stream(assertableMatches)
                        .noneMatch(assertableMatch -> assertableMatch.isEqualTo(constraintMatch))) {
                    fail("The constraintMatch (" + constraintMatch + ") is in excess,"
                            + " it's not in the assertableMatches (" + Arrays.toString(assertableMatches) + ").");
                }
            }
            assertEquals(assertableMatches.length, constraintMatchTotal.getConstraintMatchCount());
        }
        assertEquals(scoreTotal, score.getScore());
    }

    protected static AssertableMatch assertMatch(Object... justifications) {
        return new AssertableMatch(-1, justifications);
    }

    protected static AssertableMatch assertMatchWithScore(int score, Object... justifications) {
        return new AssertableMatch(score, justifications);
    }

    protected static class AssertableMatch {

        private final int score;
        private final List<Object> justificationList;

        public AssertableMatch(int score, Object... justifications) {
            this.justificationList = Arrays.asList(justifications);
            this.score = score;
        }

        public boolean isEqualTo(ConstraintMatch constraintMatch) {
            if (score != ((SimpleScore) constraintMatch.getScore()).getScore()) {
                return false;
            }
            List<Object> actualJustificationList = constraintMatch.getJustificationList();
            // Can't simply compare the lists, since the elements may be in different orders. The order is not relevant.
            if (actualJustificationList.size() != justificationList.size()) {
                return false;
            }
            return justificationList.containsAll(actualJustificationList);
        }

        @Override
        public String toString() {
            return justificationList + "=" + score;
        }

    }

}
