"""
Switch turnout actuation and interlocking controller
"""
from typing import Dict, Optional
from config import SwitchState
from world.graph import RailwayGraph, SwitchNode

class SwitchController:
    """
    Handles switch state commands, interlocking safety checks, and turnout routing.
    """
    def __init__(self, graph: RailwayGraph):
        self.graph = graph

    def set_switch_state(self, switch_id: str, new_state: SwitchState) -> bool:
        node = self.graph.nodes.get(switch_id)
        if not isinstance(node, SwitchNode):
            return False
        if node.is_locked:
            return False

        node.current_state = new_state
        return True

    def lock_switch(self, switch_id: str) -> bool:
        node = self.graph.nodes.get(switch_id)
        if isinstance(node, SwitchNode):
            node.is_locked = True
            return True
        return False

    def unlock_switch(self, switch_id: str) -> bool:
        node = self.graph.nodes.get(switch_id)
        if isinstance(node, SwitchNode):
            node.is_locked = False
            return True
        return False
