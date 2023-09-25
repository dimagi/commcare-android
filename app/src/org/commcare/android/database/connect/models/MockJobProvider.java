package org.commcare.android.database.connect.models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Temporary class for creating mock ConnectJobs
 *
 * @author dviggiano
 */
public class MockJobProvider {
    private static final List<ConnectJob> mockAvailableJobs;
    private static final List<ConnectJob> mockClaimedJobs;

    static {
        mockAvailableJobs = new ArrayList();
        mockAvailableJobs.add(
                new ConnectJob(1, "Infant Vaccine Check",
                        "You will conduct 100 home visits to assess if children below 2 years are up to date with their vaccine shots.",
                        ConnectJob.STATUS_AVAILABLE_NEW,
                        0, 100, 5, 5, 100,
                        new Date("11/1/2023"),
                        null,
//                        new ConnectJobLearningModule[]{
//                                new ConnectJobLearningModule("How to check the vaccine booklet of a child in the household", 2, new Date("8/10/2023")),
//                                new ConnectJobLearningModule("How to help the caregiver in the household get the next vaccine shot for their child", 2, null),
//                        },
                        new ArrayList<>()
                ));
        mockAvailableJobs.add(new ConnectJob(2, "Vitamin A Delivery",
                "You will deliver Vitamin A supplements to 50 homes.",
                ConnectJob.STATUS_AVAILABLE,
                0, 100, 5, 5, 100,
                new Date("11/1/2023"),
                null,
//                new ConnectJobLearningModule[]{
//                        new ConnectJobLearningModule("Sample learning module", 2, null),
//                },
                new ArrayList<>()
        ));
        mockAvailableJobs.add(new ConnectJob(3, "Training Complete Example",
                "This mock shows when training is complete for a job.",
                ConnectJob.STATUS_AVAILABLE,
                0, 100, 5, 5, 100,
                new Date("11/1/2023"),
                null,
//                new ConnectJobLearningModule[]{
//                        new ConnectJobLearningModule("Sample learning module", 2, new Date("8/10/2023")),
//                },
                new ArrayList<>()
        ));

        mockClaimedJobs = new ArrayList<>();

        List<ConnectJobDelivery> deliveries = new ArrayList<>();
        deliveries.add(new ConnectJobDelivery("Steve", new Date("7/20/2023"), "Accepted", true));
        deliveries.add(new ConnectJobDelivery("Dalitso", new Date("7/24/2023"), "Pending Verification", false));
        deliveries.add(new ConnectJobDelivery("Chimango", new Date("7/19/2023"), "Rejected", false));

        mockClaimedJobs.add(new ConnectJob(1, "Mental Health Visits",
                "",
                ConnectJob.STATUS_DELIVERING,
                60, 100, 5, 5, 100,
                new Date("11/1/2023"),
                null,
//                new ConnectJobLearningModule[]{},
                deliveries));
        mockClaimedJobs.add(new ConnectJob(2, "TestA",
                "",
                ConnectJob.STATUS_DELIVERING,
                0, 100, 5, 5, 100,
                new Date("11/1/2023"),
                null,
//                new ConnectJobLearningModule[]{},
                new ArrayList<>()));
        mockClaimedJobs.add(new ConnectJob(3, "Infant Health Check",
                "",
                ConnectJob.STATUS_DELIVERING,
                100, 100, 5, 5, 100,
                new Date("10/1/2023"),
                new Date("4/7/2023"),
//                new ConnectJobLearningModule[]{},
                new ArrayList<>()));
    }

    public static List<ConnectJob> getAvailableJobs() {
        return mockAvailableJobs;
    }

    public static List<ConnectJob> getTrainingJobs() {
        return mockAvailableJobs;
    }

    public static List<ConnectJob> getClaimedJobs() {
        return mockClaimedJobs;
    }
}
