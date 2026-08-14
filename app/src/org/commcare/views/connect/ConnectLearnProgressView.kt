package org.commcare.views.connect

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import org.commcare.AppUtils
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord
import org.commcare.connect.ConnectDateUtils
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectLearnProgressBinding
import java.text.DateFormat

/**
 * Full-screen Connect view for an opportunity whose learning is still under way — that is,
 * everything short of a passed assessment, which [ConnectLearnCompleteView] renders instead.
 *
 * Shows the opportunity header, a [ConnectProgressCard] carrying the module count and the
 * failed-assessment banner, a non-interactive "Continue Learning" card, and a [ConnectCtaBar]
 * pinned below the scrolling content. Call [bind] to populate it; the view derives its whole
 * appearance from the job and holds no state of its own, so re-binding fully re-renders.
 */
class ConnectLearnProgressView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        private val binding =
            ViewConnectLearnProgressBinding.inflate(LayoutInflater.from(context), this)

        init {
            orientation = VERTICAL
        }

        fun bind(
            job: ConnectJobRecord,
            onCtaClick: OnClickListener,
        ) {
            bindHeader(job)
            bindProgressCard(job)
            bindContinueCard(job)
            bindCtaBar(job, onCtaClick)
        }

        private fun bindHeader(job: ConnectJobRecord) {
            binding.learnProgressJobTitle.text = job.title
            binding.learnProgressExpiryValue.text =
                ConnectDateUtils.formatDate(job.projectEndDate, DateFormat.SHORT)
        }

        private fun bindProgressCard(job: ConnectJobRecord) {
            val failed = !job.modulesRemaining() && job.attemptedAssessment()

            binding.learnProgressCard.bind(
                ConnectProgressCard.State(
                    linearProgress =
                        ConnectProgressCard.State.LinearProgress(
                            label = context.getString(R.string.connect_learn_modules_completed_label),
                            current = job.completedLearningModules,
                            max = job.numLearningModules,
                            caption = context.getString(R.string.connect_learn_unlock_assessment_caption),
                        ),
                    info =
                        if (failed) {
                            ConnectProgressCard.State.Info(
                                message = context.getString(R.string.connect_learn_assessment_failed_banner),
                            )
                        } else {
                            null
                        },
                ),
            )
        }

        /**
         * The "Continue Learning" card points at a module — the next unfinished one while modules
         * remain, or the last completed one after a failed attempt — and otherwise at the
         * assessment, which is also the fallback when no module record is available to name.
         */
        private fun bindContinueCard(job: ConnectJobRecord) {
            // The API returns modules unordered and the job is loaded in payload order, so they are
            // sequenced by server id here rather than in the shared loader. Modules stored before
            // that id was persisted all carry 0, leaving payload order as the tie-break.
            val modules =
                job.learnAppInfo.learnModules
                    .orEmpty()
                    .sortedWith(compareBy({ it.moduleId }, { it.moduleIndex }))

            // Every learn app ends in an assessment, so it is the meaningful fallback whenever no
            // module can be named — either all are done, or the module records have not synced.
            if ((!job.modulesRemaining() && !job.attemptedAssessment()) || modules.isEmpty()) {
                bindContinueCardContent(
                    label = context.getString(R.string.connect_learn_up_next_label),
                    title = context.getString(R.string.connect_learn_assessment_name),
                    subtitle = context.getString(R.string.connect_learn_assessment_description),
                )
                return
            }

            val module = continueModule(job, modules)

            bindContinueCardContent(
                label =
                    context.getString(
                        if (job.modulesRemaining()) {
                            R.string.connect_learn_up_next_label
                        } else {
                            R.string.connect_learn_completed_module_label
                        },
                    ),
                title = "${modules.indexOf(module) + 1}. ${module.name}",
                subtitle =
                    resources.getQuantityString(
                        R.plurals.connect_opportunity_estimated_hours,
                        module.timeEstimate,
                        module.timeEstimate,
                    ),
            )
        }

        /**
         * Once every module is done the card names the most recently completed one; while modules
         * remain it names the first unfinished one. Both are resolved through the server ids on the
         * completion records, since modules can be completed in any order.
         *
         * Modules stored before that id was persisted all carry 0, which matches no completion and
         * would pin the card to the first module. Those fall back to the completed count — the
         * previous, order-assuming behaviour — until the next sync repopulates the ids.
         */
        private fun continueModule(
            job: ConnectJobRecord,
            modules: List<ConnectLearnModuleSummaryRecord>,
        ): ConnectLearnModuleSummaryRecord {
            if (!job.modulesRemaining()) {
                return lastCompletedModule(job, modules) ?: modules.last()
            }

            if (modules.any { it.moduleId == UNKNOWN_MODULE_ID }) {
                return modules[job.completedLearningModules.coerceIn(0, modules.size - 1)]
            }

            val completedIds =
                job.learnings
                    .orEmpty()
                    .map { it.moduleId }
                    .toSet()
            return modules.firstOrNull { it.moduleId !in completedIds } ?: modules.first()
        }

        /** The module behind the most recent completion, which is the one the retry state names. */
        private fun lastCompletedModule(
            job: ConnectJobRecord,
            modules: List<ConnectLearnModuleSummaryRecord>,
        ): ConnectLearnModuleSummaryRecord? {
            val latest = job.learnings.orEmpty().maxByOrNull { it.date } ?: return null
            return modules.firstOrNull { it.moduleId == latest.moduleId }
        }

        private fun bindContinueCardContent(
            label: CharSequence,
            title: CharSequence,
            subtitle: CharSequence,
        ) {
            binding.learnProgressContinueLabel.text = label
            binding.learnProgressContinueTitle.text = title
            binding.learnProgressContinueSubtitle.text = subtitle
        }

        private fun bindCtaBar(
            job: ConnectJobRecord,
            onCtaClick: OnClickListener,
        ) {
            binding.learnProgressCtaBar.apply {
                buttonText =
                    if (AppUtils.isAppInstalled(job.learnAppInfo.appId)) {
                        context.getString(R.string.connect_learn_cta_start)
                    } else {
                        context.getString(R.string.connect_download_learn)
                    }
                subtitleText = ctaSubtitle(job)
                infoMessage =
                    if (job.isFinished) context.getString(R.string.connect_learn_warning_ended) else null
                setOnCtaClickListener(onCtaClick)
            }
        }

        private fun ctaSubtitle(job: ConnectJobRecord): CharSequence =
            when {
                job.modulesRemaining() -> {
                    resources.getQuantityString(
                        R.plurals.connect_opportunity_learn_modules_label,
                        job.numLearningModules,
                    )
                }

                job.attemptedAssessment() -> {
                    context.getString(R.string.connect_learn_cta_take_assessment)
                }

                else -> {
                    context.getString(R.string.connect_learn_cta_complete_assessment)
                }
            }

        companion object {
            /** Value a module carries when it predates the server id being persisted. */
            private const val UNKNOWN_MODULE_ID = 0
        }
    }
