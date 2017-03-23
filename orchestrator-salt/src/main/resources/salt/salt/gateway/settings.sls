{% if grains['init'] == 'upstart' %}
    {% set is_systemd = False %}
{% else %}
    {% set is_systemd = True %}
{% endif %}
{% set is_local_ldap = salt['pillar.get']('ldap:local', False) %}

{% set gateway = {} %}
{% do gateway.update({
    'is_systemd' : is_systemd,
    'is_local_ldap' : is_local_ldap
}) %}