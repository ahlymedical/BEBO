When automating multiple Extracts in Premiere via ExtendScript:
Setting inPoint/outPoint on sequence and running 'Extract' using executeCommand(3001) often fails to delete the clips reliably if clips are not selected, or if tracks are not targeted, or due to execution latency in a loop.
