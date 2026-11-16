"""Read evaluation storage evidence through the owned QA database connection."""
import re


def stage_database(ids, query):
    if not ids:
        return {"verified": False, "sessions": {}}
    if not all(re.fullmatch(r"[1-9][0-9]{0,18}", ident) for ident in ids):
        raise ValueError("bad session ID")
    selected = ",".join(sorted(ids, key=int))
    sql = ("SELECT s.id,s.status,COALESCE(s.fail_reason,'-'),COUNT(DISTINCT e.id),COUNT(DISTINCT n.note_idx),"
           "s.analysis_attempt,COALESCE(DATE_FORMAT(s.analysis_started_at,'%Y-%m-%dT%H:%i:%s.%fZ'),'-'),"
           "COALESCE(DATE_FORMAT(e.evaluated_at,'%Y-%m-%dT%H:%i:%s.%fZ'),'-'),COALESCE(e.model_version,'-') "
           "FROM practice_sessions s LEFT JOIN evaluations e ON e.session_id=s.id "
           "LEFT JOIN note_results n ON n.evaluation_id=e.id "
           f"WHERE s.id IN ({selected}) GROUP BY s.id,s.status,s.fail_reason,s.analysis_attempt,s.analysis_started_at,e.evaluated_at,e.model_version")
    evidence = {}
    for line in query(sql).splitlines():
        sid, status, fail, evals, notes, attempts, queued, saved, model = line.split("\t")
        evidence[sid] = {"status": status, "failReason": None if fail == "-" else fail, "evaluations": int(evals),
                         "notes": int(notes), "attempts": int(attempts), "analysisStartedAt": None if queued == "-" else queued,
                         "evaluationSavedAt": None if saved == "-" else saved, "modelVersion": None if model == "-" else model}
    return {"verified": len(evidence) == len(ids), "sessions": evidence}
