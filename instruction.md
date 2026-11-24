Implement deterministic recording and replay for Jenkins Pipeline in the workflow-cps plugin.

Add a block step named deterministicReplay with mode set to record or verify and a required tapePath. Reject invalid paths, unknown modes, or nested blocks with errors containing tapePath must be a relative path, Unknown deterministicReplay mode, or cannot be nested.

In record mode, execute the body and save the steps and outcomes for later verification. In verify mode, compare the body with the recorded tape, replay recorded step outcomes without repeating side effects, and execute passthrough steps normally. Abort the build on drift, extra or missing steps, or mismatched arguments or step names.

A tape is stored under the build's replay-tapes directory. If no earlier recorded tape is available, the build fails with a message containing recorded execution tape <tapePath>.
