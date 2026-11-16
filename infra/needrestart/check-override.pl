#!/usr/bin/perl
# S15P31E102-758: needrestart가 서비스마다 자동 재시작할지(restart) 미룰지(defer)를
# /usr/sbin/needrestart 3.6의 override_rc 판정과 같은 방식으로 출력한다. 서비스를 재시작하지 않는다.
# 사용: sudo perl check-override.pl [설정 파일] 서비스...
#   설정 파일 기본값은 /etc/needrestart/needrestart.conf(끝에서 conf.d/*.conf를 읽는다).
use warnings;
our $LOGPREF = '[check-override]';
our %nrconf = (verbosity => 1, defno => 0, override_rc => {});
my $conf = (@ARGV && $ARGV[0] =~ m{/}) ? shift @ARGV : '/etc/needrestart/needrestart.conf';
die "usage: check-override.pl [conf] service...\n" unless @ARGV;
eval do { local(@ARGV, $/) = $conf; <> };
die "Error parsing $conf: $@" if $@;
for my $rc (@ARGV) {
    my $restart = !$nrconf{defno};
    foreach my $re (keys %{$nrconf{override_rc}}) {
        next unless $rc =~ /$re/;
        $restart = $nrconf{override_rc}->{$re};
        last;
    }
    printf "%-36s %s\n", $rc, $restart ? 'restart' : 'defer';
}
