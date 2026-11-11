"""Apply Task707 using the existing exclusive ingress gate and drain procedure.

The shared gate name/lock also prevents concurrent Task728 operations. Recovery
and receipt semantics are those of publish_score_tempo_quiesced.py.
"""
import publish_airplane_revision
from publish_score_tempo_quiesced import main


if __name__ == '__main__':
    main(publish_airplane_revision)
