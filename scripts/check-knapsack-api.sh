#!/bin/bash

URL="https://knapsack-api.quuux.org/"
STATUS="$(curl -vs $URL 2> /tmp/check-knapsack.log|tee /tmp/check-knapsack.json|jq -r .status)"


if [ "$STATUS" != "ok" ]; then
    echo
    echo "Date:     `date`"
    echo "Error:    $URL did not return status=ok"
    echo "Request:"
    echo
    cat /tmp/check-knapsack.log
    echo
    echo "Response:"
    echo
    cat /tmp/check-knapsack.json
    echo
fi
